package com.flowershop.flowershop.repository;

import java.math.BigDecimal;

public interface SalesReportRow {

    String getPeriod();

    Long getOrders();

    BigDecimal getRevenue();

    Long getItemsSold();
}