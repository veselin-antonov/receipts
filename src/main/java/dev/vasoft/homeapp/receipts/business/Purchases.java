package dev.vasoft.homeapp.receipts.business;

import dev.vasoft.homeapp.receipts.api.request.ReqPurchase;
import dev.vasoft.homeapp.receipts.api.response.ResPage;
import dev.vasoft.homeapp.receipts.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.business.mappers.PurchaseMapper;
import dev.vasoft.homeapp.receipts.model.entities.Product;
import dev.vasoft.homeapp.receipts.model.entities.Purchase;
import dev.vasoft.homeapp.receipts.model.entities.Store;
import dev.vasoft.homeapp.receipts.model.repositories.CustomRepository;
import dev.vasoft.homeapp.receipts.model.repositories.ProductRepository;
import dev.vasoft.homeapp.receipts.model.repositories.PurchasesRepository;
import dev.vasoft.homeapp.receipts.model.repositories.StoresRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class Purchases {
	private final Logger logger;

	private final PurchasesRepository purchasesRepository;
	private final CustomRepository customRepository;
	private final ProductRepository productRepository;
	private final StoresRepository storesRepository;

	@Autowired
	public Purchases(PurchasesRepository purchasesRepository,
					 CustomRepository customRepository,
					 ProductRepository productRepository,
					 StoresRepository storesRepository) {
		this.logger = LoggerFactory.getLogger(Purchases.class);
		this.purchasesRepository = purchasesRepository;
		this.customRepository = customRepository;
		this.productRepository = productRepository;
		this.storesRepository = storesRepository;
	}

	public ResPage<ResPurchase> getPurchasesPage(int pageNumber, int pageSize,
												 String searchQuery) {
		if (pageSize == 0) {
			pageSize = 10;
		}

		Pageable paging = PageRequest.of(pageNumber, pageSize,
										 Sort.by("date").descending());

		Page<Purchase> purchasesPage;

		if (searchQuery == null || searchQuery.isEmpty()) {
			purchasesPage = purchasesRepository.findAll(paging);
		} else {
			purchasesPage = customRepository.findBySearchQuery(searchQuery,
															   paging);
		}

		List<ResPurchase> resPurchases = purchasesPage.stream()
													  .map(PurchaseMapper::toResPurchase)
													  .toList();

		return new ResPage<>(resPurchases, pageNumber,
							 purchasesPage.getTotalPages());
	}


	@SuppressWarnings("OptionalGetWithoutIsPresent")
	public ResPurchase registerPurchase(ReqPurchase reqPurchase) {
		Store store;

		Optional<Store> existingStore = storesRepository.findByName(
				reqPurchase.store());
		if (existingStore.isPresent()) {
			store = existingStore.get();
		} else {
			Store newStore = new Store(reqPurchase.store());
			store = storesRepository.save(newStore);
		}

		Product product;

		Optional<Product> existingProduct = productRepository.findByName(
				reqPurchase.product());
		if (existingProduct.isPresent()) {
			product = existingProduct.get();
		} else {
			Product newProduct = new Product(reqPurchase.product());
			product = productRepository.save(newProduct);
		}

		//TODO: Primitive field validation
		Purchase purchase = new Purchase(product, reqPurchase.price(),
										 reqPurchase.date(), store,
										 reqPurchase.discount());

		purchase = purchasesRepository.save(purchase);

		return PurchaseMapper.toResPurchase(purchase);
	}
}
