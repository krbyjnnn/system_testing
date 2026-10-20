package com.flowershop.flowershop.repository;

public interface SpoilageRow {

    String getItemType();

    String getItemName();

    Integer getQuantityOnHand();

    String getExpiryDate();

    String getUpdatedAt();
}