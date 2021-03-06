/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.epics.ca.impl.monitor.disruptor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.DataProvider;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.FatalExceptionHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.SequenceReportingEventHandler;
import com.lmax.disruptor.Sequencer;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.TimeoutHandler;


/**
 * This is a modification of {@link BatchEventProcessor} that reports monitor connection loss events.
 * <p>
 * Convenience class for handling the batching semantics of consuming entries from a {@link RingBuffer}
 * and delegating the available events to an {@link EventHandler}.
 * <p>
 * If the {@link EventHandler} also implements {@link LifecycleAware} it will be notified just after the thread
 * is started and just before the thread is shutdown.
 *
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public final class MonitorBatchEventProcessor<T> implements EventProcessor
{
   private final AtomicBoolean running = new AtomicBoolean (false);
   private ExceptionHandler<? super T> exceptionHandler = new FatalExceptionHandler ();
   private final DataProvider<T> dataProvider;
   private final SequenceBarrier sequenceBarrier;
   private final EventHandler<? super T> eventHandler;
   private final Sequence sequence = new Sequence (Sequencer.INITIAL_CURSOR_VALUE);
   private final TimeoutHandler timeoutHandler;

   private final ConnectionInterruptable channel;
   private final T disconnectedValue;
   private final Predicate<T> isDisconnectedValue;

   /**
    * Construct a {@link EventProcessor} that will automatically track the progress by updating its sequence when
    * the {@link EventHandler#onEvent(Object, long, boolean)} method returns.
    *
    * @param channel reference to an object which can report on the state of the connection.
    * @param disconnectedValue the value to be published to indicate channel disconnection.
    * @param isDisconnectedValue predicate which returns true when the channel is disconnected.
    * @param dataProvider    to which events are published.
    * @param sequenceBarrier on which it is waiting.
    * @param eventHandler    is the delegate to which events are dispatched.
    */
   public MonitorBatchEventProcessor(
         final ConnectionInterruptable channel,
         final T disconnectedValue,
         final Predicate<T> isDisconnectedValue,
         final DataProvider<T> dataProvider,
         final SequenceBarrier sequenceBarrier,
         final EventHandler<? super T> eventHandler
   )
   {
      this.channel = channel;
      this.disconnectedValue = disconnectedValue;
      this.isDisconnectedValue = isDisconnectedValue;

      this.dataProvider = dataProvider;
      this.sequenceBarrier = sequenceBarrier;
      this.eventHandler = eventHandler;

      if ( eventHandler instanceof SequenceReportingEventHandler )
      {
         ((SequenceReportingEventHandler<?>) eventHandler).setSequenceCallback (sequence);
      }

      timeoutHandler = (eventHandler instanceof TimeoutHandler) ? (TimeoutHandler) eventHandler : null;
   }

   @Override
   public Sequence getSequence()
   {
      return sequence;
   }

   @Override
   public void halt()
   {
      running.set (false);
      sequenceBarrier.alert ();
   }

   @Override
   public boolean isRunning()
   {
      return running.get ();
   }

   /**
    * Set a new {@link ExceptionHandler} for handling exceptions propagated out of the {@link MonitorBatchEventProcessor}
    *
    * @param exceptionHandler to replace the existing exceptionHandler.
    */
   public void setExceptionHandler( final ExceptionHandler<? super T> exceptionHandler )
   {
      if ( null == exceptionHandler )
      {
         throw new NullPointerException ();
      }

      this.exceptionHandler = exceptionHandler;
   }

   /**
    * It is ok to have another thread rerun this method after a halt().
    *
    * @throws IllegalStateException if this object instance is already running in a thread
    */
   @Override
   public void run()
   {
      if ( !running.compareAndSet (false, true) )
      {
         throw new IllegalStateException ("Thread is already running");
      }
      sequenceBarrier.clearAlert ();

      notifyStart ();

      int lastConnectionLossId = channel.getConnectionLossId ();

      T event = null;
      long nextSequence = sequence.get () + 1L;
      try
      {
         while ( true )
         {
            try
            {
               final long availableSequence = sequenceBarrier.waitFor( nextSequence );

               while ( nextSequence <= availableSequence )
               {
                  event = dataProvider.get (nextSequence);
                  if ( isDisconnectedValue.test (event) )
                     lastConnectionLossId = channel.getConnectionLossId ();
                  eventHandler.onEvent (event, nextSequence, nextSequence == availableSequence);
                  nextSequence++;
               }

               sequence.set (availableSequence);
            }
            catch ( final TimeoutException e )
            {
               notifyTimeout (sequence.get ());
            }
            catch ( final AlertException ex )
            {
               if ( !running.get () )
               {
                  break;
               }
            }
            catch ( final Throwable ex )
            {
               exceptionHandler.handleEventException (ex, nextSequence, event);
               sequence.set (nextSequence);
               nextSequence++;
            }

            try
            {
               final int connectionLossId = channel.getConnectionLossId ();
               if ( lastConnectionLossId != connectionLossId )
               {
                  lastConnectionLossId = connectionLossId;
                  eventHandler.onEvent (disconnectedValue, -1, true);
               }
            }
            catch ( final TimeoutException e )
            {
               notifyTimeout (-1);
            }
            catch ( final AlertException ex )
            {
               if ( !running.get () )
               {
                  break;
               }
            }
            catch ( final Throwable ex )
            {
               exceptionHandler.handleEventException (ex, -1, event);
            }

         }
      }
      finally
      {
         notifyShutdown ();
         running.set (false);
      }
   }

   private void notifyTimeout( final long availableSequence )
   {
      try
      {
         if ( timeoutHandler != null )
         {
            timeoutHandler.onTimeout (availableSequence);
         }
      }
      catch ( Throwable e )
      {
         exceptionHandler.handleEventException (e, availableSequence, null);
      }
   }

   /**
    * Notifies the EventHandler when this processor is starting up
    */
   private void notifyStart()
   {
      if ( eventHandler instanceof LifecycleAware )
      {
         try
         {
            ((LifecycleAware) eventHandler).onStart ();
         }
         catch ( final Throwable ex )
         {
            exceptionHandler.handleOnStartException (ex);
         }
      }
   }

   /**
    * Notifies the EventHandler immediately prior to this processor shutting down
    */
   private void notifyShutdown()
   {
      if ( eventHandler instanceof LifecycleAware )
      {
         try
         {
            ((LifecycleAware) eventHandler).onShutdown ();
         }
         catch ( final Throwable ex )
         {
            exceptionHandler.handleOnShutdownException (ex);
         }
      }
   }
}