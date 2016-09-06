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

package co.vaughnvernon.algotrader.domain.model.order;

import java.math.BigDecimal;
import java.util.UUID;

import com.gemstone.gemfire.cache.Operation;
import com.gemstone.gemfire.cache.query.CqEvent;

import co.vaughnvernon.tradercommon.event.DomainEvent;
import co.vaughnvernon.tradercommon.event.DomainEventGemFirePublisher;
import co.vaughnvernon.tradercommon.event.DomainEventListener;
import co.vaughnvernon.tradercommon.event.StoredEvent;
import co.vaughnvernon.tradercommon.monetary.Money;
import co.vaughnvernon.tradercommon.quote.Quote;
import co.vaughnvernon.tradercommon.quote.TickerSymbol;
import junit.framework.TestCase;

public class AlgoOrderGemFireTest extends TestCase {

	private AlgoOrderFilled algoOrderFilled;
	private AlgoSliceOrderSharesRequested sliceOrderSharesRequestedEvent;
	private BigDecimal totalQuantity = BigDecimal.ZERO;

	public AlgoOrderGemFireTest() {
		super();
	}

	public void testCreate() throws Exception {
		AlgoOrder algoOrder = new AlgoOrder(UUID.randomUUID().toString().toUpperCase(), OrderType.Buy,
				new Quote(new TickerSymbol("GOOG"), new Money("725.50"), 500));

		assertNotNull(algoOrder);
		assertFalse(algoOrder.isFilled());
		assertTrue(algoOrder.hasSharesRemaining());
		assertEquals(new BigDecimal("500"), algoOrder.sharesRemaining());

		try {
			algoOrder.fill();

			fail("Should not be filled and should throw exception.");

		} catch (IllegalStateException e) {
			// good, ignore
		}
	}

	public void testRequestSlice() throws Exception {

		DomainEventListener aListener = new DomainEventListener("testBuyOrderCreation") {
			@Override
			public void onEvent(CqEvent cqEvent) {
				Operation baseOperation = cqEvent.getBaseOperation();

				if (baseOperation.isCreate()) {
					StoredEvent storedEvent = (StoredEvent) cqEvent.getNewValue();
					DomainEvent aDomainEvent = storedEvent.toDomainEvent();
					if (aDomainEvent instanceof AlgoSliceOrderSharesRequested) {
						sliceOrderSharesRequestedEvent = (AlgoSliceOrderSharesRequested) aDomainEvent;
					}
				}
			}
		};
		DomainEventGemFirePublisher.instance().subscribe(aListener);

		AlgoOrder algoOrder = new AlgoOrder(UUID.randomUUID().toString().toUpperCase(), OrderType.Buy,
				new Quote(new TickerSymbol("GOOG"), new Money("725.50"), 500));

		algoOrder.requestSlice(new Money("725.10"), 100);

		for (int i = 0; i < 5; i++) {
			Thread.sleep(500);
			if (sliceOrderSharesRequestedEvent != null) {
				break;
			}
		}
		DomainEventGemFirePublisher.instance().unsubscribe();

		assertNotNull(sliceOrderSharesRequestedEvent);
		assertEquals(new BigDecimal("100"), sliceOrderSharesRequestedEvent.quantity());
		assertFalse(algoOrder.isFilled());
		assertTrue(algoOrder.hasSharesRemaining());
		assertEquals(new BigDecimal("400"), algoOrder.sharesRemaining());

		try {
			algoOrder.fill();

			fail("Should not be filled and should throw exception.");

		} catch (IllegalStateException e) {
			// good, ignore
		}
	}

	public void testFillAlgoOrder() throws Exception {

		DomainEventListener aListener1 = new DomainEventListener("testFillAlgoOrder") {
			@Override
			public void onEvent(CqEvent cqEvent) {
				Operation baseOperation = cqEvent.getBaseOperation();

				if (baseOperation.isCreate()) {
					StoredEvent storedEvent = (StoredEvent) cqEvent.getNewValue();
					DomainEvent aDomainEvent = storedEvent.toDomainEvent();
					if (aDomainEvent instanceof AlgoSliceOrderSharesRequested) {
						AlgoSliceOrderSharesRequested anAlgoSliceOrderSharesRequestedEvent = (AlgoSliceOrderSharesRequested) aDomainEvent;
						totalQuantity = totalQuantity.add(anAlgoSliceOrderSharesRequestedEvent.quantity());
					}
				}
			}
		};
		
		DomainEventListener aListener2 = new DomainEventListener("testFillAlgoOrder") {
			@Override
			public void onEvent(CqEvent cqEvent) {
				Operation baseOperation = cqEvent.getBaseOperation();

				if (baseOperation.isCreate()) {
					StoredEvent storedEvent = (StoredEvent) cqEvent.getNewValue();
					DomainEvent aDomainEvent = storedEvent.toDomainEvent();
					if (aDomainEvent instanceof AlgoOrderFilled) {
						AlgoOrderFilled anAlgoOrderFilledEvent = (AlgoOrderFilled) aDomainEvent;
						algoOrderFilled = anAlgoOrderFilledEvent;
					}
				}
			}
		};

		DomainEventGemFirePublisher.instance().subscribe(new DomainEventListener[] {aListener1, aListener2});

		AlgoOrder algoOrder = new AlgoOrder(UUID.randomUUID().toString().toUpperCase(), OrderType.Buy,
				new Quote(new TickerSymbol("GOOG"), new Money("725.50"), 500));

		for (int idx = 0; idx < 5; ++idx) {
			algoOrder.requestSlice(new Money("725.10"), 100);
		}

		for (int i = 0; i < 5; i++) {
			Thread.sleep(500);
			if (algoOrderFilled != null) {
				break;
			}
		}
		DomainEventGemFirePublisher.instance().unsubscribe();

		assertEquals(new BigDecimal("500"), this.totalQuantity);
		assertNotNull(algoOrderFilled);
		assertEquals(algoOrder.orderId(), algoOrderFilled.orderId());
		assertTrue(algoOrder.isFilled());
		assertFalse(algoOrder.hasSharesRemaining());
		assertEquals(BigDecimal.ZERO, algoOrder.sharesRemaining());
		assertNotNull(algoOrder.fill());

		assertEquals(algoOrder.quote().price(), algoOrder.fill().price());
		assertEquals(algoOrder.quote().quantity(), algoOrder.fill().quantity().intValue());
		assertNotNull(algoOrder.fill().filledOn());
	}
}
