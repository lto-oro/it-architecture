package com.group4.case2;

public record DecisionInput(
        String orderNr,
        String clientNr,
        String deliveryCountry,
        String deliveryAddress,
        int weightKg,
        String phone,
        String mail
) {
}
