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

package co.vaughnvernon.nanotrader.application.order;

import java.util.Collection;

import co.vaughnvernon.nanotrader.domain.model.order.BuyOrder;
import co.vaughnvernon.nanotrader.domain.model.order.BuyOrderRepository;
import co.vaughnvernon.nanotrader.domain.model.order.FillService;
import co.vaughnvernon.tradercommon.quote.TickerSymbol;
import co.vaughnvernon.tradercommon.quotebar.QuoteBar;

public class BuyOrderApplicationService {

	private BuyOrderRepository buyOrderRepository;
	private FillService fillService;

	public BuyOrderApplicationService(
			BuyOrderRepository aBuyOrderRepository,
			FillService aFillService) {

		super();

		this.buyOrderRepository = aBuyOrderRepository;
		this.fillService = aFillService;
	}

	public void fillOrdersUsing(QuoteBar aQuoteBar) {

		TickerSymbol tickerSymbol = new TickerSymbol(aQuoteBar.symbol());

		int totalQuantityAvailable =
				aQuoteBar
					.totalQuantity()
					.toBigInteger()
					.intValue();

		Collection<BuyOrder> openOrders =
				this.buyOrderRepository().openOrdersOf(tickerSymbol);

		this.fillService().fillBuyOrders(openOrders, totalQuantityAvailable);
	}

	private BuyOrderRepository buyOrderRepository() {
		return this.buyOrderRepository;
	}

	private FillService fillService() {
		return this.fillService;
	}
}
