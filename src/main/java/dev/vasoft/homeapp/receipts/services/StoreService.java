package dev.vasoft.homeapp.receipts.services;

import dev.vasoft.homeapp.receipts.api.response.ResStore;
import dev.vasoft.homeapp.receipts.model.repositories.StoresRepository;
import dev.vasoft.homeapp.receipts.services.mappers.StoreMapper;
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
            .findAll(Sort.by("name").ascending())
            .stream()
            .map(StoreMapper::toResStore)
            .toList();
    }
}
