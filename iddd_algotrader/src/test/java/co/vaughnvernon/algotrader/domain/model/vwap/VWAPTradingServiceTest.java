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

package co.vaughnvernon.algotrader.domain.model.vwap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.gemstone.gemfire.cache.Operation;
import com.gemstone.gemfire.cache.query.CqEvent;

import co.vaughnvernon.algotrader.domain.model.order.AlgoOrder;
import co.vaughnvernon.algotrader.domain.model.order.AlgoOrderFilled;
import co.vaughnvernon.algotrader.domain.model.order.AlgoOrderRepository;
import co.vaughnvernon.algotrader.domain.model.order.AlgoSliceOrderSharesRequested;
import co.vaughnvernon.algotrader.domain.model.order.OrderType;
import co.vaughnvernon.algotrader.infrastructure.persistence.InMemoryAlgoOrderRepository;
import co.vaughnvernon.algotrader.infrastructure.persistence.InMemoryVWAPAnalyticRepository;
import co.vaughnvernon.tradercommon.event.DomainEvent;
import co.vaughnvernon.tradercommon.event.DomainEventGemFirePublisher;
import co.vaughnvernon.tradercommon.event.DomainEventListener;
import co.vaughnvernon.tradercommon.event.StoredEvent;
import co.vaughnvernon.tradercommon.monetary.Money;
import co.vaughnvernon.tradercommon.pricevolume.PriceVolume;
import co.vaughnvernon.tradercommon.quote.Quote;
import co.vaughnvernon.tradercommon.quote.TickerSymbol;
import co.vaughnvernon.tradercommon.quotebar.QuoteBar;
import junit.framework.TestCase;

public class VWAPTradingServiceTest extends TestCase {

	private boolean algoOrderFilled;
	private AlgoOrder algoOrder;
	private AlgoOrderRepository algoOrderRepository;
	private int algoSharesSent;
	private VWAPAnalyticRepository vwapAnalyticRepository;
	private VWAPTradingService vwapTradingService;

	public VWAPTradingServiceTest() {
		super();
	}

	public void testFillAlgoOrdersUsing() throws Exception {
		DomainEventListener aListener1 = new DomainEventListener("algoSliceOrderSharesRequestedCQ") {
			@Override
			public void onEvent(CqEvent cqEvent) {
				Operation baseOperation = cqEvent.getBaseOperation();

				if (baseOperation.isCreate()) {
					StoredEvent storedEvent = (StoredEvent) cqEvent.getNewValue();
					DomainEvent aDomainEvent = storedEvent.toDomainEvent();
					if (aDomainEvent instanceof AlgoSliceOrderSharesRequested) {
						AlgoSliceOrderSharesRequested event = (AlgoSliceOrderSharesRequested) aDomainEvent;
						algoSharesSent += event.quantity().intValue();
						System.out.println("Received " + event.quantity().intValue() + " shares");
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
						algoOrderFilled = true;
					}
				}
			}
		};

		DomainEventGemFirePublisher.instance().subscribe(new DomainEventListener[] {aListener1, aListener2});

		QuoteBar quoteBar = this.quoteBarValue();

		int totalQuantity = 0;
		if (algoOrder != null)
			totalQuantity = this.algoOrder.quote().quantity();

		for (int count = 0; count < totalQuantity; count += 100) {
			this.vwapTradingService.tradeUnfilledBuyOrdersUsing(quoteBar);
		}
		for (int count = 0; count < totalQuantity; count += 100) {
			this.vwapTradingService.tradeUnfilledBuyOrdersUsing(quoteBar);
		}		

		for (int i = 0; i < 5; i++) {
			Thread.sleep(500);
			if (algoOrderFilled) {
				break;
			}
		}
		DomainEventGemFirePublisher.instance().unsubscribe();

		assertTrue(algoOrderFilled);
		assertFalse(this.algoOrder.hasSharesRemaining());
		assertNotNull(this.algoOrder.fill());
		assertEquals(totalQuantity, algoSharesSent);
	}

	@Override
	protected void setUp() throws Exception {
		this.algoOrderRepository = InMemoryAlgoOrderRepository.instance();
		this.vwapAnalyticRepository = InMemoryVWAPAnalyticRepository.instance();

		this.vwapTradingService =
				new VWAPTradingService(
						this.vwapAnalyticRepository,
						this.algoOrderRepository);

		VWAPAnalytic vwapAnalytic = this.vwapAnalyticAggregate();

		this.vwapAnalyticRepository.save(vwapAnalytic);

		this.saveAlgoOrder(algoOrderRepository);

		super.setUp();
	}

	private void saveAlgoOrder(AlgoOrderRepository algoOrderRepository) {
		this.algoOrder =
				new AlgoOrder(
						UUID.randomUUID().toString(),
						OrderType.Buy,
						new Quote(new TickerSymbol("ORCL"), new Money("32.50"), 701));

		assertTrue(this.algoOrder.hasSharesRemaining());

		this.algoOrderRepository.save(this.algoOrder);
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

	private QuoteBar quoteBarValue() {
		Collection<PriceVolume> priceVolumes =
				this.priceVolumeValues(
						new Money("32.20"),
						new BigDecimal("25"));

		QuoteBar quoteBar =
				new QuoteBar(
						"Oracle, Inc.",
						"ORCL",
						new Money("32.10"),
						new Money("32.25"),
						new Money("32.15"),
						new Money("33.30"),
						new Money("31.85"),
						new BigDecimal("20"),
						priceVolumes,
						new BigDecimal("400"),
						priceVolumes.size());

		return quoteBar;
	}

	private VWAPAnalytic vwapAnalyticAggregate() {
		Money price = new Money("32.15");
		BigDecimal volume = new BigDecimal("1000.00");
		PriceVolume priceVolume = new PriceVolume(price, volume);

		VWAPAnalytic vwapAnalytic = new VWAPAnalytic("ORCL", priceVolume);

		for (int idx = 0; idx < VWAPAnalytic.TRADABLE_QUOTE_BARS; ++idx) {
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
