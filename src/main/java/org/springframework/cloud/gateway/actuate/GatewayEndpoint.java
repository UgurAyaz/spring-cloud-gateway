package org.springframework.cloud.gateway.actuate;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.api.Route;
import org.springframework.cloud.gateway.api.RouteLocator;
import org.springframework.cloud.gateway.api.RouteWriter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.route.RouteFilter;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.support.CachingRouteLocator;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
//TODO: move to new Spring Boot 2.0 actuator when ready
//@ConfigurationProperties(prefix = "endpoints.gateway")
@RestController
@RequestMapping("/admin/gateway")
public class GatewayEndpoint {/*extends AbstractEndpoint<Map<String, Object>> {*/

	private static final Log log = LogFactory.getLog(GatewayEndpoint.class);

	private RouteLocator routeLocator;
	private List<GlobalFilter> globalFilters;
	private List<RouteFilter> routeFilters;
	private FilteringWebHandler filteringWebHandler;
	private RouteWriter routeWriter;

	public GatewayEndpoint(RouteLocator routeLocator, List<GlobalFilter> globalFilters,
						   List<RouteFilter> routeFilters, FilteringWebHandler filteringWebHandler,
						   RouteWriter routeWriter) {
		//super("gateway");
		this.routeLocator = routeLocator;
		this.globalFilters = globalFilters;
		this.routeFilters = routeFilters;
		this.filteringWebHandler = filteringWebHandler;
		this.routeWriter = routeWriter;
	}

	/*@Override
	public Map<String, Object> invoke() {
	}*/

	//TODO: this should really be a listener that responds to a RefreshEvent
	@PostMapping("/refresh")
	public Flux<Route> refresh() {
		if (this.routeLocator instanceof CachingRouteLocator) {
			return ((CachingRouteLocator)this.routeLocator).refresh();
		}
		return Flux.empty();
	}

	@GetMapping("/globalfilters")
	public Map<String, Object> globalfilters() {
		return getNamesToOrders(this.globalFilters);
	}

	@GetMapping("/routefilters")
	public Map<String, Object> routefilers() {
		return getNamesToOrders(this.routeFilters);
	}

	private <T> Map<String, Object> getNamesToOrders(List<T> list) {
		HashMap<String, Object> filters = new HashMap<>();

		for (Object o : list) {
			Integer order = null;
			if (o instanceof Ordered) {
				order = ((Ordered)o).getOrder();
			}
			//filters.put(o.getClass().getName(), order);
			filters.put(o.toString(), order);
		}

		return filters;
	}

	@GetMapping("/routes")
	public Mono<List<Route>> routes() {
		return this.routeLocator.getRoutes().collectList();
	}

/*
http POST :8080/admin/gateway/routes/addreqhead2 \
uri=http://httpbin.org/headers \
predicates:='["Host=**.addrequestheader.org", "Url=/headers"]' \
filters:='["AddRequestHeader=X-Request-Foo, Bar"]'
*/
	@PostMapping("/routes/{id}")
	public Mono<ResponseEntity<Void>> save(@PathVariable String id, @RequestBody Mono<Route> route) {
		return this.routeWriter.save(route.map(r ->  {
			r.setId(id);
			log.debug("Saving route: " + route);
			return r;
		})).then(() -> {
			GatewayEndpoint.this.refresh();
			return Mono.just(ResponseEntity.created(URI.create("/routes/"+id)).build());
		});
	}

	@DeleteMapping("/routes/{id}")
	public Mono<ResponseEntity<Void>> delete(@PathVariable Mono<String> id) {
		return this.routeWriter.delete(id).then(() -> {
			GatewayEndpoint.this.refresh();
			return Mono.just(ResponseEntity.ok().build());
		});
	}

	@GetMapping("/routes/{id}")
	public Mono<Route> route(@PathVariable String id) {
		return this.routeLocator.getRoutes()
				.filter(route -> route.getId().equals(id))
				.singleOrEmpty();
	}

	@GetMapping("/routes/{id}/combinedfilters")
	public Map<String, Object> combinedfilters(@PathVariable String id) {
		Mono<Route> route = this.routeLocator.getRoutes()
				.filter(r -> r.getId().equals(id))
				.singleOrEmpty();
		Optional<Route> optional = Optional.ofNullable(route.block()); //TODO: remove block();
		return getNamesToOrders(this.filteringWebHandler.combineFiltersForRoute(optional));
	}
}