package com.sdprep.fooddelivery;

/**
 * Delivery ETA = max(food prep time, courier travel to restaurant) + travel to customer.
 * The max() matters: the courier waiting for food and the food waiting for the courier both delay delivery.
 */
public final class EtaCalculator {

    static final double COURIER_SPEED_KM_PER_HOUR = 20;

    private EtaCalculator() {
    }

    public static long etaMinutes(int prepMinutes, Location courier, Location restaurant, Location customer) {
        double toRestaurant = travelMinutes(courier.distanceKmTo(restaurant));
        double toCustomer = travelMinutes(restaurant.distanceKmTo(customer));
        return Math.round(Math.max(prepMinutes, toRestaurant) + toCustomer);
    }

    private static double travelMinutes(double km) {
        return km / COURIER_SPEED_KM_PER_HOUR * 60;
    }
}
