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

package co.vaughnvernon.nanotrader.controller.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.gemstone.gemfire.cache.Operation;
import com.gemstone.gemfire.cache.query.CqEvent;

import co.vaughnvernon.algotrader.application.ApplicationServiceRegistry;
import co.vaughnvernon.algotrader.application.vwap.VWAPApplicationService;
import co.vaughnvernon.algotrader.domain.model.order.AlgoOrderFilled;
import co.vaughnvernon.algotrader.domain.model.order.AlgoSliceOrderSharesRequested;
import co.vaughnvernon.algotrader.domain.model.vwap.VWAPAnalytic;
import co.vaughnvernon.algotrader.domain.model.vwap.VWAPAnalyticRepository;
import co.vaughnvernon.algotrader.infrastructure.persistence.InMemoryAlgoOrderRepository;
import co.vaughnvernon.algotrader.infrastructure.persistence.InMemoryVWAPAnalyticRepository;
import co.vaughnvernon.nanotrader.domain.model.order.BuyOrder;
import co.vaughnvernon.tradercommon.event.DomainEvent;
import co.vaughnvernon.tradercommon.event.DomainEventGemFirePublisher;
import co.vaughnvernon.tradercommon.event.DomainEventListener;
import co.vaughnvernon.tradercommon.event.DomainEventPublisher;
import co.vaughnvernon.tradercommon.event.StoredEvent;
import co.vaughnvernon.tradercommon.monetary.Money;
import co.vaughnvernon.tradercommon.order.AccountId;
import co.vaughnvernon.tradercommon.order.BuyOrderPlaced;
import co.vaughnvernon.tradercommon.pricevolume.PriceVolume;
import co.vaughnvernon.tradercommon.quote.Quote;
import co.vaughnvernon.tradercommon.quote.TickerSymbol;
import co.vaughnvernon.tradercommon.quotebar.QuoteBar;
import co.vaughnvernon.tradercommon.quotebar.QuoteBarDispatcherFactory;
import co.vaughnvernon.tradercommon.quotebar.QuoteBarInterest;
import co.vaughnvernon.tradercommon.quotefeed.QuoteFeederRunner;
import junit.framework.TestCase;

public class AlgoOrderApplicationIntegrationTest extends TestCase {


	private static final int NUMBER_OF_SHARES = 999;
	private static final Money PRICE = new Money("35.10");
	private static final String TICKER = "ORCL";
	private boolean wasAlgoOrderPlaced = false;
	private boolean wasAlgoOrderFilled = false;
	private int algoOrderSharesToSubmit = 0;
	private int quoteBarCount = 0;
	int totalQuantity = 0;

	public AlgoOrderApplicationIntegrationTest() {
		super();
	}

	public void testCreateBuyOrderAndExecuteAlgorithmically() throws Exception {
		DomainEventListener aListener1 = new DomainEventListener("buyOrderPlacedCq") {
			@Override
			public void onEvent(CqEvent cqEvent) {
				Operation baseOperation = cqEvent.getBaseOperation();

				if (baseOperation.isCreate()) {
					StoredEvent storedEvent = (StoredEvent) cqEvent.getNewValue();
					DomainEvent aDomainEvent = storedEvent.toDomainEvent();
					if (aDomainEvent instanceof BuyOrderPlaced) {
						BuyOrderPlaced event = (BuyOrderPlaced) aDomainEvent;
						algoOrderSharesToSubmit = event.quantityOfSharesOrdered();
						co.vaughnvernon.algotrader.application.ApplicationServiceRegistry
							.algoOrderApplicationService().createAlgoBuyOrder(
								event.orderId().id(), event.quote().tickerSymbol().symbol(), event.quote().price(),
								algoOrderSharesToSubmit);
						wasAlgoOrderPlaced = true;
					}
				}
			}
		};

		DomainEventListener aListener2 = new DomainEventListener("algoOrderFilledCQ") {
			@Override
			public void onEvent(CqEvent cqEvent) {
				Operation baseOperation = cqEvent.getBaseOperation();

				if (baseOperation.isCreate()) {
					StoredEvent storedEvent = (StoredEvent) cqEvent.getNewValue();
					DomainEvent aDomainEvent = storedEvent.toDomainEvent();
					if (aDomainEvent instanceof AlgoOrderFilled) {
						wasAlgoOrderFilled = true;
					}
					else if (aDomainEvent instanceof AlgoSliceOrderSharesRequested) {
						AlgoSliceOrderSharesRequested event = (AlgoSliceOrderSharesRequested) aDomainEvent;
						System.out.println("slice=" + event.quantity());
					}
				}
			}
		};

		DomainEventGemFirePublisher.instance().subscribe(new DomainEventListener[] {aListener1, aListener2});

		BuyOrder buyOrder = this.buyOrderFixture();

		for (int i=0; i<5 && !wasAlgoOrderPlaced; i++) {
			Thread.sleep(100);
		}
		
		VWAPApplicationService vwapApplicationService =
				ApplicationServiceRegistry
				.vwapApplicationService();
		QuoteBar aQuoteBar = this.quoteBarValue();
		vwapApplicationService.fillAlgoOrdersUsing(aQuoteBar);
		
		assertNotNull(buyOrder);
		assertEquals(NUMBER_OF_SHARES, buyOrder.quantityOfOutstandingShares());


		if (algoOrderSharesToSubmit > 0)
			totalQuantity = algoOrderSharesToSubmit;

		QuoteBarInterest interest = new QuoteBarInterest() {
			@Override
			public void inform(QuoteBar aQuoteBar) {
				System.out.println("" + aQuoteBar);
				
				int aQuoteQuantity = aQuoteBar.volume().intValue();
				int sharesToTradeForQuote = Math.min(aQuoteQuantity, totalQuantity);
				if (aQuoteBar.symbol().equals("ORCL")) {
					System.out.println("volume=" + sharesToTradeForQuote + ". wasAlgoOrderFilled=" + wasAlgoOrderFilled);
				}
				for (int shares = 0; shares < sharesToTradeForQuote && !wasAlgoOrderFilled; shares += 100) {
					vwapApplicationService.fillAlgoOrdersUsing(aQuoteBar);
				}

				++quoteBarCount;
			}
		};

		QuoteBarDispatcherFactory
			.instance()
			.dispatcher()
			.registerQuoteBarInterest(interest);

		QuoteFeederRunner.instance().run();

//		Thread.sleep(8100L);

		/*
		 * Stream quotes here
		 */
//		for (int count = 0; count < totalQuantity; count += 100) {
//			vwapApplicationService.fillAlgoOrdersUsing(aQuoteBar);
//		}

		for (int i = 0; i < 2500; i++) {
			Thread.sleep(100);
			if (wasAlgoOrderFilled) {
				break;
			}
		}
		DomainEventGemFirePublisher.instance().unsubscribe();

		assertTrue(wasAlgoOrderFilled);
//		assertFalse(this.algoOrder.hasSharesRemaining());
//		assertNotNull(this.algoOrder.fill());
//		assertEquals(totalQuantity, algoSharesSent);
//
//		assertEquals(1,
//				InMemoryAlgoOrderRepository.instance().unfilledBuyAlgoOrdersOfSymbol(new TickerSymbol("GOOG")).size());
	}

	@Override
	protected void setUp() throws Exception {
		DomainEventPublisher.instance().reset();

		super.setUp();
		
		VWAPAnalytic vwapAnalytic = this.vwapAnalyticAggregate();

		VWAPAnalyticRepository vwapAnalyticRepository = InMemoryVWAPAnalyticRepository.instance();
		vwapAnalyticRepository.save(vwapAnalytic);
	}

	private BuyOrder buyOrderFixture() {
		return new BuyOrder(
				AccountId.unique(),
				new Quote(new TickerSymbol(TICKER), PRICE),
				NUMBER_OF_SHARES,
				new Money("9.99"));
	}
	private QuoteBar quoteBarValue() {
		Collection<PriceVolume> priceVolumes =
				this.priceVolumeValues(
						new Money("35.20"),
						new BigDecimal("25"));

		QuoteBar quoteBar =
				new QuoteBar(
						"Oracle, Inc.",
						TICKER,
						new Money("35.10"),
						new Money("35.25"),
						new Money("35.15"),
						new Money("36.30"),
						new Money("34.85"),
						new BigDecimal("20"),
						priceVolumes,
						new BigDecimal("500"),
						priceVolumes.size());

		return quoteBar;
	}

	private Collection<PriceVolume> priceVolumeValues(
			Money aBasePrice,
			BigDecimal aBaseVolume) {

		List<PriceVolume> priceVolumes = new ArrayList<PriceVolume>();
		Money price = aBasePrice;
		BigDecimal volume = aBaseVolume;

		for (int idx = 0; idx < 100; ++idx) {
			if ((idx % 2) == 0) {
				price = price.addedTo(new Money("0.02"));
			} else if ((idx % 3) == 0) {
				price = price.subtract(new Money("0.03"));
			} else {
				price = price.addedTo(new Money("0.01"));
			}

			volume = volume.add(aBaseVolume);

			priceVolumes.add(new PriceVolume(price, volume));
		}

		return priceVolumes;
	}

	private VWAPAnalytic vwapAnalyticAggregate() {
		Money price = new Money("32.15");
		BigDecimal volume = new BigDecimal("1000.00");
		PriceVolume priceVolume = new PriceVolume(price, volume);

		VWAPAnalytic vwapAnalytic = new VWAPAnalytic("ORCL", priceVolume);

		for (int idx = 0; idx < VWAPAnalytic.tradableBars(); ++idx) {
			if ((idx % 2) == 0) {
				price = price.addedTo(new Money("0.03"));
				volume = volume.add(new BigDecimal("20.0"));
			} else {
				price = price.subtract(new Money("0.05"));
				volume = volume.add(new BigDecimal("25.0"));
			}

			vwapAnalytic.accumulatePriceVolume(new PriceVolume(price, volume));
		}

		assertTrue(vwapAnalytic.isReadyToTrade());

		return vwapAnalytic;
	}
}
