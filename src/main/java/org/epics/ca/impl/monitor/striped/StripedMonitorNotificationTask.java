/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor.striped;

import heinz.StripedRunnable;
import net.jcip.annotations.Immutable;
import org.apache.commons.lang3.Validate;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Runnable for transferring a monitor notification to a consumer.
 *
 * @param <T> the type of the object to transfer. May sometimes refer to
 *           monitor metadata or simply the most recent monitor value.
 */
@Immutable
class StripedMonitorNotificationTask<T> implements StripedRunnable
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   // Get Logger
   private static final Logger logger = Logger.getLogger( StripedMonitorNotificationTask.class.getName() );

   private final T value;
   private final Consumer<? super T> valueConsumer;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a new instance which when run will transfer a single value
    * object of type T to the Consumer.
    *
    * @param valueConsumer the consumer.
    * @param value the value.
    */
    StripedMonitorNotificationTask( Consumer<? super T> valueConsumer, T value )
    {
       this.valueConsumer = Validate.notNull( valueConsumer );
       this.value = Validate.notNull( value );
    }


/*- Public methods -----------------------------------------------------------*/

   /**
    * Transfers a single value to the consumer.
    */
   @Override
   public void run()
   {
      try
      {
         //logger.log(Level.FINEST, String.format( "Notifying consumer '%s' with value: '%s'... ", valueConsumer, value ) );
         valueConsumer.accept( value );
         //logger.log(Level.FINEST, "Notification completed ok");
      }
      catch ( RuntimeException ex )
      {
         logger.log(Level.WARNING, "Unexpected exception during transfer. Message was: '%s'", ex);
         ex.printStackTrace();
      }
   }

   /**
    * Returns the so-called "Stripe" of this object.
    *
    * Since we want each Consumer to be in the same Stripe so that they get executed
    * sequentially we simply return a reference to the associated Consumer.
    *
    * @return an object reference which will be interpreted as this objects stripe.
    */
   @Override
   public Object getStripe()
   {
      return valueConsumer;
   }


/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
