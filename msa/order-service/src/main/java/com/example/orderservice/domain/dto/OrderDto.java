package com.example.orderservice.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderDto implements Serializable {

    private String productId;

    private Integer qty;

    private Integer unitPrice;

    private Integer totalPrice;

    private String orderId;

    private String username;
}
