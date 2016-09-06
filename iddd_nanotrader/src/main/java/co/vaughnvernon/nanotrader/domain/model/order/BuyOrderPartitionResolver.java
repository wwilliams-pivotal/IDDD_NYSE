package co.vaughnvernon.nanotrader.domain.model.order;

import com.gemstone.gemfire.cache.PartitionResolver;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gemstone.gemfire.cache.Declarable;
import com.gemstone.gemfire.cache.EntryOperation;

/**
 * Enforces colocation of the BuyOrder Region with Orders and Fills by symbol
 * @author wwilliams
 *
 */
public class BuyOrderPartitionResolver  implements PartitionResolver<String, BuyOrder>, Declarable {

  private static final Logger LOG = LoggerFactory.getLogger(BuyOrderPartitionResolver.class);
  
  /**
   * Overrides the partition key by returning the key of the Inventories region
   */
  @Override
  public Object getRoutingObject(EntryOperation<String, BuyOrder> opDetails) {
    String key = opDetails.getKey();
    BuyOrder buyOrder = opDetails.getNewValue();
    if (buyOrder == null) {
      // return the propertyId so that availabilities can be colocated with Inventories
      return key;
   }
    return buyOrder.quote().tickerSymbol();
  }

  @Override
  public String getName() {
    return this.getClass().getName();
  }

  @Override
  public void close() {
  }

  @Override
  public void init(Properties props) {
    LOG.info("Setting up the " + this.getClass().getName());
  }
}
