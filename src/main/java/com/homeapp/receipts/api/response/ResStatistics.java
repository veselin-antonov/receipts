package com.homeapp.receipts.api.response;

import lombok.Data;

import java.util.List;

@Data
public class ResStatistics {

    @Data
    public static class Common {
        private final String value;
        private final String label;
    }

    @Data
    public static class Store {
        private final ResStore store;
        private final String label;
    }

    private final List<Common> commons;
    private final Store usualStore;

}
