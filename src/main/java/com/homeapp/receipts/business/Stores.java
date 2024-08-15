package com.homeapp.receipts.business;

import com.homeapp.receipts.api.response.ResStore;
import com.homeapp.receipts.model.repositories.StoresRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class Stores {
	private StoresRepository storesRepository;

	@Autowired
	public Stores(StoresRepository storesRepository) {
		this.storesRepository = storesRepository;
	}

	public List<ResStore> getALl() {
		return storesRepository.findAll().stream().map(store -> new ResStore(
									   store.getId().toHexString(), store.getName(),
									   store.getIconID())).sorted(Comparator.comparing(ResStore::name))
							   .toList();
	}
}
