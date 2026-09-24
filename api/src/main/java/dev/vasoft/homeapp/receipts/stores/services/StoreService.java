package dev.vasoft.homeapp.receipts.stores.services;

import dev.vasoft.homeapp.receipts.stores.api.response.ResStore;
import dev.vasoft.homeapp.receipts.stores.model.entities.Store;
import dev.vasoft.homeapp.receipts.common.model.repositories.StoresRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class StoreService {

    private final StoresRepository storesRepository;

    @Autowired
    public StoreService(StoresRepository storesRepository) {
        this.storesRepository = storesRepository;
    }

    public List<ResStore> getAll() {
        return storesRepository
            .findAll(Sort.by("canonicalName").ascending())
            .stream()
            .map(StoreMapper::toResStore)
            .toList();
    }

    public Store findStoreByNameFuzzy(String name) {
        // TODO
        return null;
    }
}