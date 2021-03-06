package org.epics.ca.impl.requests;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.epics.ca.CompletionException;
import org.epics.ca.Status;
import org.epics.ca.impl.ChannelImpl;
import org.epics.ca.impl.ContextImpl;
import org.epics.ca.impl.Messages;
import org.epics.ca.impl.NotifyResponseRequest;
import org.epics.ca.impl.Transport;
import org.epics.ca.impl.TypeSupports.TypeSupport;

/**
 * CA read notify.
 */
public class ReadNotifyRequest<T> extends CompletableFuture<T> implements NotifyResponseRequest
{

   /**
    * Context.
    */
   protected final ContextImpl context;

   /**
    * I/O ID given by the context when registered.
    */
   protected final int ioid;

   /**
    * Channel server ID.
    */
   protected final int sid;

   /**
    * Channel.
    */
   protected final ChannelImpl<?> channel;

   /**
    * Type support.
    */
   protected final TypeSupport<T> typeSupport;


   /**
    *
    * @param channel the channel.
    * @param transport the transport.
    * @param sid the CA Server ID.
    * @param typeSupport reference to an object which can provide support for this type.
    */
   public ReadNotifyRequest( ChannelImpl<?> channel, Transport transport, int sid, TypeSupport<T> typeSupport )
   {

      this.channel = channel;
      this.sid = sid;
      this.typeSupport = typeSupport;

      int dataCount = typeSupport.getForcedElementCount ();

      if ( dataCount == 0 && channel.getTransport ().getMinorRevision () < 13 )
         dataCount = channel.getNativeElementCount ();

      context = transport.getContext ();
      ioid = context.registerResponseRequest (this);
      channel.registerResponseRequest (this);

      Messages.readNotifyMessage (transport, typeSupport.getDataType (), dataCount, sid, ioid);
      transport.flush ();
   }

   @Override
   public int getIOID()
   {
      return ioid;
   }

   @Override
   public void response( int status, short dataType, int dataCount, ByteBuffer dataPayloadBuffer )
   {
      try
      {

         Status caStatus = Status.forStatusCode (status);
         if ( caStatus == Status.NORMAL )
         {
            T value = null;   // NOTE: reserved for "reuse" option
            value = typeSupport.deserialize (dataPayloadBuffer, value, dataCount);

            complete (value);
         }
         else
         {
            completeExceptionally (caStatus, caStatus.getMessage ());
         }

      }
      finally
      {
         // always cancel request
         cancel ();
      }
   }

   @Override
   public void cancel()
   {
      // unregister response request
      context.unregisterResponseRequest (this);
      channel.unregisterResponseRequest (this);
   }

   @Override
   public void exception( int errorCode, String errorMessage )
   {
      cancel ();

      Status status = Status.forStatusCode (errorCode);
      if ( status == null )
         status = Status.GETFAIL;

      completeExceptionally (status, errorMessage);
   }

   protected void completeExceptionally( Status status, String message )
   {
      completeExceptionally (new CompletionException (status, message));
   }
}
