package co.vaughnvernon.nanotrader.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import co.vaughnvernon.nanotrader.domain.model.order.BuyOrder;
import co.vaughnvernon.nanotrader.domain.model.order.BuyOrderRepository;
import co.vaughnvernon.tradercommon.monetary.Money;
import co.vaughnvernon.tradercommon.order.AccountId;
import co.vaughnvernon.tradercommon.order.OrderId;
import co.vaughnvernon.tradercommon.quote.Quote;
import co.vaughnvernon.tradercommon.quote.TickerSymbol;
import junit.framework.TestCase;

public class GemFireBuyOrderRepositoryTest extends TestCase {

	private BuyOrderRepository repository;

	public GemFireBuyOrderRepositoryTest() {
		super();

		this.repository = new GemFireBuyOrderRepository();
	}

	public void testOpenOrdersOf() throws Exception {
		this.repository.save(this.buyOrderFixture1());

		this.repository.save(this.buyOrderFixture2());

		this.repository.save(this.buyOrderFixture3());

		Collection<BuyOrder> foundOrders = this.repository.openOrdersOf(this.googTickerFixture());

		assertNotNull(foundOrders);

		assertFalse(foundOrders.isEmpty());

		assertEquals(2, foundOrders.size());
	}

	public void testOrderOf() throws Exception {
		BuyOrder buyOrder = this.buyOrderFixture1();

		this.repository.save(buyOrder);

		BuyOrder foundOrder = this.repository.orderOf(buyOrder.orderId());

		assertNotNull(foundOrder);

		assertEquals(buyOrder, foundOrder);
	}

	public void testRemove() throws Exception {

		BuyOrder buyOrder = this.buyOrderFixture1();

		this.repository.save(buyOrder);

		BuyOrder foundOrder = this.repository.orderOf(buyOrder.orderId());

		assertEquals(buyOrder, foundOrder);

		this.repository.remove(buyOrder);

		foundOrder = this.repository.orderOf(buyOrder.orderId());

		assertNull(foundOrder);
	}

	public void testSave() throws Exception {

		List<OrderId> ids = new ArrayList<OrderId>();

		for (int idx = 0; idx < 10; ++idx) {

			BuyOrder order = this.buyOrderFixture1();

			this.repository.save(order);

			ids.add(order.orderId());
		}

		for (int idx = 0; idx < 10; ++idx) {

			BuyOrder order = this.repository.orderOf(ids.get(idx));

			assertNotNull(order);
		}
	}

	public void testSaveWithOverwrite() throws Exception {
		BuyOrder buyOrder = this.buyOrderFixture1();

		this.repository.save(buyOrder);

		assertTrue(buyOrder.isOpen());

		assertFalse(buyOrder.isFilled());

		BuyOrder changedBuyOrder = this.repository.orderOf(buyOrder.orderId());

		changedBuyOrder.sharesToPurchase(changedBuyOrder.execution().quantityOfSharesOrdered());

		this.repository.save(changedBuyOrder);

		changedBuyOrder = this.repository.orderOf(buyOrder.orderId());

		assertNotNull(changedBuyOrder.holdingOfFilledOrder());

		// the following two tests are reversed from the InMemoryRepo because
		// buyOrder is persisted and would not be changed
		assertTrue(buyOrder.isOpen());

		assertFalse(buyOrder.isFilled());
	}

	private BuyOrder buyOrderFixture1() {
		BuyOrder buyOrder = new BuyOrder(AccountId.unique(), new Quote(this.googTickerFixture(), new Money("731.30")),
				2, new Money("12.99"));

		return buyOrder;
	}

	private BuyOrder buyOrderFixture2() {
		BuyOrder buyOrder = new BuyOrder(AccountId.unique(), new Quote(this.googTickerFixture(), new Money("730.89")),
				5, new Money("12.99"));

		return buyOrder;
	}

	private BuyOrder buyOrderFixture3() {
		BuyOrder buyOrder = new BuyOrder(AccountId.unique(), new Quote(new TickerSymbol("MSFT"), new Money("27.71")),
				20, new Money("12.99"));

		return buyOrder;
	}

	private TickerSymbol googTickerFixture() {
		return new TickerSymbol("GOOG");
	}
}
