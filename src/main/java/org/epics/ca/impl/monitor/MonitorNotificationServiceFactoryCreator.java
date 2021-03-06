/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.math.NumberUtils;
import org.epics.ca.impl.monitor.blockingqueue.BlockingQueueMonitorNotificationServiceFactory;
import org.epics.ca.impl.monitor.disruptor.DisruptorMonitorNotificationServiceFactory;
import org.epics.ca.impl.monitor.striped.StripedExecutorServiceMonitorNotificationServiceFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class MonitorNotificationServiceFactoryCreator implements AutoCloseable
{

/*- Public attributes --------------------------------------------------------*/

   enum ServiceImpl
   {
      BlockingQueueSingleWorkerMonitorNotificationServiceImpl,
      BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,
      DisruptorOldMonitorNotificationServiceImpl,
      DisruptorNewMonitorNotificationServiceImpl,
      StripedExecutorServiceMonitorNotificationServiceImpl;

      private static final ServiceImpl[] copyOfValues = values();

      public static boolean isRecognised( String name )
      {
         for ( ServiceImpl value : copyOfValues )
         {
            if (value.name().equals(name))
            {
               return true;
            }
         }
         return false;
      }
   }

   /**
    * This definition configures the behaviour of the CA library V_1_1_0 release.
    *
    * @implNote
    * This release used a BlockingQueueMonitorNotificationService with 5 queue workers.
    */
   public static final String V1_1_0_DEFAULT_IMPL = ServiceImpl.BlockingQueueMultipleWorkerMonitorNotificationServiceImpl.name() + ",5";

   /**
    * This definition configures the behaviour that is applicable for a wide range of clients.
    *
    * @implNote
    * The current implementation uses a BlockingQueueMultipleWorkerMonitorNotificationService running with 16 notification threads.
    * This may change in future releases.
    */
   public static final String DEFAULT_IMPL = ServiceImpl.BlockingQueueMultipleWorkerMonitorNotificationServiceImpl.name() + ",16";

   /**
    * This definition configures the behaviour for a client that does not mind dropping intermediate events to keep up
    * with the notification rate.
    *
    * @implNote
    * The current implementation uses a BlockingQueueMultipleWorkerMonitorNotificationServiceImpl running with 100
    * notification threads and a non-buffering queue.
    *
    * This may change in future releases.
    */
   public static final String HUMAN_CONSUMER_IMPL = ServiceImpl.BlockingQueueMultipleWorkerMonitorNotificationServiceImpl + ",100,1";

   /**
    * This definition configures the behaviour for a client which requires events to be buffered to keep up with the
    * notification rate during busy periods.
    *
    * @implNote
    * The current implementation uses a StripedExecutorMonitorNotificationService running with 100 notification threads.
    * This may change in future releases.
    */
   public static final String MACHINE_CONSUMER_IMPL = ServiceImpl.BlockingQueueMultipleWorkerMonitorNotificationServiceImpl.name() + ",100";

   /**
    * The number of service threads that will be used by default for service implementations which require
    * more than one thread.
    *
    * @implNote
    * This definition currently applies to the BlockingQueueMultipleWorkerMonitorNotificationServiceImpl and
    * StripedExecutorServiceMonitorNotificationService service implementations.
    */
   public static final int NUMBER_OF_SERVICE_THREADS_DEFAULT = 10;

   /**
    * The size of the notification value buffer which will be used by default.
    *
    * @implNote
    * This definition currently applies to the BlockingQueueSingleWorkerMonitorNotificationServiceImpl,
    * BlockingQueueMultipleWorkerMonitorNotificationServiceImpl service implementations.
    */
   public static final int NOTIFICATION_VALUE_BUFFER_SIZE_DEFAULT = Integer.MAX_VALUE;

/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = Logger.getLogger( MonitorNotificationServiceFactoryCreator.class.getName() );

   private static final List<MonitorNotificationServiceFactory> serviceFactoryList = new ArrayList<>();


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a factory which will generate service instances based on the
    * specified service implementation.
    *
    * The following properties are supported:
    * <ul>
    * <li> BlockingQueueSingleWorkerMonitorNotificationServiceImpl,NumberOfThreads,BufferSize</li>
    * <li> BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,NumberOfThreads,BufferSize</li>
    * <li> DisruptorOldMonitorNotificationServiceImpl</li>
    * <li> DisruptorNewMonitorNotificationServiceImpl</li>
    * <li> StripedExecutorServiceMonitorNotificationServiceImpl,NumberOfThreads</li>
    * </ul>
    *
    * @param serviceConfiguration specifies the properties of the service instances that
    *                    this factory will generate.
    *
    * @return the factory.
    *
    * @throws IllegalArgumentException if the serviceImpl string was of the
    * incorrect format.
    */
   public static MonitorNotificationServiceFactory create( String serviceConfiguration )
   {
      logger.log( Level.FINEST, String.format("MonitorNotificationServiceFactoryCreator has been called with serviceImpl specifier: %s", serviceConfiguration ) );

      Validate.notEmpty( serviceConfiguration );

      final String[] args = serviceConfiguration.split( "," );
      Validate.isTrue( args.length >= 1 );
      Validate.isTrue( ServiceImpl.isRecognised(args[ 0 ] ), "The service configuration '" + args[ 0 ] + "' was not recognised." );

      final ServiceImpl serviceImpl = ServiceImpl.valueOf(args[ 0 ] );

      final MonitorNotificationServiceFactory serviceFactory ;
      switch( serviceImpl )
      {
         case StripedExecutorServiceMonitorNotificationServiceImpl:
         {
            int totalNumberOfServiceThreads = (args.length == 2) ? NumberUtils.toInt(args[ 1 ], NUMBER_OF_SERVICE_THREADS_DEFAULT) : NUMBER_OF_SERVICE_THREADS_DEFAULT;
            serviceFactory = new StripedExecutorServiceMonitorNotificationServiceFactory( totalNumberOfServiceThreads );
         }
         break;

         case DisruptorOldMonitorNotificationServiceImpl:
         {
            serviceFactory = new DisruptorMonitorNotificationServiceFactory(true);
         }
         break;

         case DisruptorNewMonitorNotificationServiceImpl:
         {
            serviceFactory = new DisruptorMonitorNotificationServiceFactory(false);
         }
         break;

         case BlockingQueueSingleWorkerMonitorNotificationServiceImpl:
         {
            if( (args.length >= 2) )
            {
               logger.log( Level.INFO, "Note: in this implementation the value for the number of notification threads will be ignored and set to 1." );
            }

            final int totalNumberOfServiceThreads = 1;
            final int notificationValueBufferQueueSize = (args.length == 3) ? NumberUtils.toInt(args[ 2 ], NOTIFICATION_VALUE_BUFFER_SIZE_DEFAULT ) : NOTIFICATION_VALUE_BUFFER_SIZE_DEFAULT ;

            serviceFactory = new BlockingQueueMonitorNotificationServiceFactory( totalNumberOfServiceThreads, notificationValueBufferQueueSize );
         }
         break;

         default:
         case BlockingQueueMultipleWorkerMonitorNotificationServiceImpl:
         {
            final int totalNumberOfServiceThreads = (args.length >= 2) ? NumberUtils.toInt(args[ 1 ], NUMBER_OF_SERVICE_THREADS_DEFAULT)
                                                             : NUMBER_OF_SERVICE_THREADS_DEFAULT;

            final int notificationValueBufferQueueSize = (args.length == 3) ? NumberUtils.toInt(args[ 2 ], NOTIFICATION_VALUE_BUFFER_SIZE_DEFAULT ) : NOTIFICATION_VALUE_BUFFER_SIZE_DEFAULT ;

            serviceFactory = new BlockingQueueMonitorNotificationServiceFactory( totalNumberOfServiceThreads, notificationValueBufferQueueSize );
         }
         break;
      }
      serviceFactoryList.add( serviceFactory );
      return serviceFactory;
   }

/*- Public methods -----------------------------------------------------------*/

   @Override
   public void close()
   {
      serviceFactoryList.forEach( MonitorNotificationServiceFactory::close );
      serviceFactoryList.clear();
   }

   /**
    * Returns a list of all service implementations recognised by this factory.
    *
    * @return the list.
    */
   public static List<String> getAllServiceImplementations()
   {
      return Arrays.stream(ServiceImpl.values() ).map( ServiceImpl::toString ).collect( Collectors.toList() );
   }

   static public long getServiceCount()
   {
      return serviceFactoryList.stream().mapToInt( MonitorNotificationServiceFactory::getServiceCount ).sum();
   }

   /**
    * Utility method to cleanly shutdown an ExecutorService
    *
    * @param executorService the service to shut down.
    */
   public static void shutdownExecutor( ExecutorService executorService )
   {
      logger.log (Level.FINEST, "Starting executor shutdown sequence..." );

      executorService.shutdown();
      try
      {
         logger.log ( Level.FINEST, "Waiting 2 seconds for tasks to finish..." );
         if ( executorService.awaitTermination(2, TimeUnit.SECONDS ) )
         {
            logger.log ( Level.FINEST, "Executor terminated ok." );
         }
         else
         {
            logger.log ( Level.FINEST, "Executor did not yet terminate. Forcing termination..." );
            executorService.shutdownNow();
            executorService.awaitTermination(2, TimeUnit.SECONDS );
         }
      }
      catch ( InterruptedException ex )
      {
         logger.log ( Level.WARNING, "Interrupted whilst waiting for tasks to finish. Propagating interrupt." );
         Thread.currentThread().interrupt();
      }
      logger.log ( Level.FINEST, "Executor shutdown sequence completed." );
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
