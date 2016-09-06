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

package co.vaughnvernon.nanotrader.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.ClientCacheFactory;

import co.vaughnvernon.nanotrader.domain.model.order.BuyOrder;
import co.vaughnvernon.nanotrader.domain.model.order.BuyOrderRepository;
import co.vaughnvernon.tradercommon.order.OrderId;
import co.vaughnvernon.tradercommon.quote.TickerSymbol;

public class GemFireBuyOrderRepository implements BuyOrderRepository {

	private static BuyOrderRepository instance;

	private static final String BUY_ORDERS_REGION = "BuyOrders";
	private Region<OrderId, BuyOrder> buyOrdersRegion;
	ClientCache clientCache;

	public static synchronized BuyOrderRepository instance() {
		if (instance == null) {
			instance = new GemFireBuyOrderRepository();
		}

		return instance;
	}

	public GemFireBuyOrderRepository() {
		super();

		initializeGemFireCache();
		this.setOrders(new HashMap<OrderId,BuyOrder>());
	}

	@Override
	public Collection<BuyOrder> openOrdersOf(TickerSymbol aTickerSymbol) {
		List<BuyOrder> openOrders = new ArrayList<BuyOrder>();

		for (OrderId oid : orders().keySetOnServer()) {
			BuyOrder order = orders().get(oid);
			if (order.hasTickerSymbol(aTickerSymbol)) {
				if (order.isOpen()) {
					openOrders.add(order);
				}
			}
		}

		return openOrders;
	}

	@Override
	public BuyOrder orderOf(OrderId anOrderId) {
		return orders().get(anOrderId);
	}

	@Override
	public void remove(BuyOrder aOrder) {
		orders().remove(aOrder.orderId());
	}

	@Override
	public void save(BuyOrder aOrder) {
		orders().put(aOrder.orderId(), aOrder);
	}

	private Region<OrderId, BuyOrder> orders() {
		return buyOrdersRegion;
	}

	private void setOrders(Map<OrderId, BuyOrder> aMap) {
		Set<OrderId> keys = orders().keySetOnServer();
		orders().removeAll(keys);
		aMap.forEach((k,v) -> orders().put(k, v));
	}
	

	private void initializeGemFireCache() {
		clientCache = ClientCacheFactory.getAnyInstance();
		if (clientCache == null || clientCache.isClosed()) {
			ClientCacheFactory ccf = new ClientCacheFactory();
			ccf.set("cache-xml-file", "./target/classes/gemfire/clientCache.xml");
			clientCache = ccf.create();
		}
		buyOrdersRegion = clientCache.getRegion(BUY_ORDERS_REGION);
	}

}
