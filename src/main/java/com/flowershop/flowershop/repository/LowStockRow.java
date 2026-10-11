package com.flowershop.flowershop.repository;

public interface LowStockRow {

    String getItemType();

    String getItemName();

    String getCategoryName();

    Long getQuantityOnHand();
}