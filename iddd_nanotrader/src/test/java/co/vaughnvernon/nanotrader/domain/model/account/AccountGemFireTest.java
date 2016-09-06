//   Copyright 2012,2013 Vaughn Vernon
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package co.vaughnvernon.nanotrader.domain.model.account;

import java.util.Calendar;

import com.gemstone.gemfire.cache.Operation;
import com.gemstone.gemfire.cache.query.CqEvent;

import co.vaughnvernon.nanotrader.domain.model.order.BuyOrder;
import co.vaughnvernon.nanotrader.domain.model.order.BuyOrderFilled;
import co.vaughnvernon.tradercommon.event.DomainEvent;
import co.vaughnvernon.tradercommon.event.DomainEventGemFirePublisher;
import co.vaughnvernon.tradercommon.event.DomainEventListener;
import co.vaughnvernon.tradercommon.event.DomainEventPublisher;
import co.vaughnvernon.tradercommon.event.StoredEvent;
import co.vaughnvernon.tradercommon.monetary.Money;
import co.vaughnvernon.tradercommon.order.BuyOrderPlaced;
import co.vaughnvernon.tradercommon.quote.TickerSymbol;
import junit.framework.TestCase;

public class AccountGemFireTest extends TestCase {

	private Account reconcilableAccount;

	public AccountGemFireTest() {
		super();
	}

	public void testOpenAccount() throws Exception {

		Profile profile = this.profileFixture();

		Money money = new Money("1000.00");

		Account account = profile.openAccount(money);

		assertNotNull(account);
		assertNotNull(account.accountId());
		assertEquals(profile.profileId(), account.profileId());
		assertEquals(money, account.openingBalance());
		assertEquals(money, account.cashBalance());
		assertEquals(0, account.holdings().size());

		Calendar establishedOn = Calendar.getInstance();
		establishedOn.setTime(account.establishedOn());
		Calendar now = Calendar.getInstance();

		assertEquals(now.get(Calendar.MONTH), establishedOn.get(Calendar.MONTH));
		assertEquals(now.get(Calendar.DATE), establishedOn.get(Calendar.DATE));
		assertEquals(now.get(Calendar.YEAR), establishedOn.get(Calendar.YEAR));
	}

	public void testPlaceBuyOrder() throws Exception {

		Money money = new Money("10000.00");

		Account account = this.profileFixture().openAccount(money);

		Money orderFee = new Money("9.99");
		Money price = new Money("723.25");
		int shares = 10;
		TickerSymbol tickerSymbol = new TickerSymbol("GOOG");

		BuyOrder buyOrder = account.placeBuyOrder(tickerSymbol, price, shares, orderFee);

		assertEquals(account.accountId(), buyOrder.accountId());
		assertEquals(tickerSymbol, buyOrder.quote().tickerSymbol());
		assertEquals(price, buyOrder.quote().price());
		assertEquals(shares, buyOrder.quantityOfSharesOrdered());
		assertEquals(shares, buyOrder.execution().quantityOfSharesOrdered());
		assertEquals(shares, buyOrder.execution().quantityOfSharesOutstanding());
	}

	public void testReconcileWith() throws Exception {

		DomainEventGemFirePublisher.instance().reset();

		DomainEventListener listener1 = new DomainEventListener("buyOrderPlacedCQ") {
			public String oql() {
				return "select * from /DomainEvents";
			}

			@Override
			public void onEvent(CqEvent cqEvent) {
				Operation baseOperation = cqEvent.getBaseOperation();

				System.out.println("I am here 1. BaseOperation=" + baseOperation.toString());
				if (baseOperation.isCreate()) {
					StoredEvent storedEvent = (StoredEvent) cqEvent.getNewValue();
					DomainEvent event = storedEvent.toDomainEvent();
					System.out.println("I am here 2. event=" + event.getClass().getSimpleName());
					if (event instanceof BuyOrderPlaced) {
						BuyOrderPlaced aDomainEvent = (BuyOrderPlaced) event;
						System.out.println("I am here 3");
						reconcilableAccount.reconcileWith(new Payment(aDomainEvent.accountId(), aDomainEvent.orderId(),
								"BUY: " + aDomainEvent.quantityOfSharesOrdered() + " of "
										+ aDomainEvent.quote().tickerSymbol() + " at " + aDomainEvent.quote().price(),
								aDomainEvent.cost(), aDomainEvent.orderFee(), aDomainEvent.placedOnDate()));
					}
				}
			}
		};
		DomainEventGemFirePublisher.instance().subscribe(listener1);

		DomainEventListener listener2 = new DomainEventListener("buyOrderFilledCQ") {
			public String oql() {
				return "select * from /DomainEvents";
			}

			@Override
			public void onEvent(CqEvent cqEvent) {
				Operation baseOperation = cqEvent.getBaseOperation();

				System.out.println("I am here Fill 1");

				if (baseOperation.isCreate()) {
					StoredEvent storedEvent = (StoredEvent) cqEvent.getNewValue();
					DomainEvent event = storedEvent.toDomainEvent();
					System.out.println("I am here Fill 2");
					if (event instanceof BuyOrderFilled) {
						System.out.println("I am here Fill 3");
						BuyOrderFilled aDomainEvent = (BuyOrderFilled) event;
						reconcilableAccount.reconcileWith(aDomainEvent.holding());
					}
				}
			}
		};

		DomainEventGemFirePublisher.instance().subscribe(listener2);
		// DomainEventGemFirePublisher.instance().subscribe(new
		// DomainEventListener[] {listener1, listener2});
		Money openingBalance = new Money("10000.00");

		reconcilableAccount = this.profileFixture().openAccount(openingBalance);

		assertTrue(reconcilableAccount.holdings().isEmpty());

		Money orderFee = new Money("9.99");
		Money price = new Money("723.25");
		int shares = 10;
		TickerSymbol tickerSymbol = new TickerSymbol("GOOG");

		BuyOrder buyOrder = reconcilableAccount.placeBuyOrder(tickerSymbol, price, shares, orderFee);

		buyOrder.sharesToPurchase(shares);

		for (int i = 0; i < 5; i++) {
			Thread.sleep(500);
			if (!reconcilableAccount.holdings().isEmpty()) {
				break;
			}
		}
		DomainEventGemFirePublisher.instance().unsubscribe();

		assertFalse(reconcilableAccount.holdings().isEmpty());
		assertEquals(1, reconcilableAccount.holdings().size());

		// the current balance should be openinBalance minus orderFee
		// since the debit of the cost and the credit of the new holding
		// is the same
		assertEquals(openingBalance.subtract(orderFee), reconcilableAccount.currentBalance());
	}

	@Override
	protected void setUp() throws Exception {
		DomainEventPublisher.instance().reset();
	}

	private Profile profileFixture() {
		Profile profile = new Profile("blah", "blahblah", "Walley Jones", "123 Main Street, Burnt Mattress, ID 83701",
				"walley@jonesnames.me", "1234 5678 9012 3456");

		return profile;
	}
}
