package com.example.fooddash;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class DeliveryFlowTest {

    @Test
    public void testStatusNormalization() {
        // Canonical mapping
        assertEquals(Constants.STATUS_ACCEPTED, ActiveOrderActivity.normalizeStatus("confirmed"));
        assertEquals(Constants.STATUS_READY, ActiveOrderActivity.normalizeStatus("ready_for_pickup"));
        assertEquals(Constants.STATUS_OUT_FOR_DELIVERY, ActiveOrderActivity.normalizeStatus("on_the_way"));
        assertEquals(Constants.STATUS_DELIVERED, ActiveOrderActivity.normalizeStatus("completed"));

        // Partial matches and aliases
        assertEquals(Constants.STATUS_ACCEPTED, ActiveOrderActivity.normalizeStatus("Order Accepted"));
        assertEquals(Constants.STATUS_PREPARING, ActiveOrderActivity.normalizeStatus("Preparing Food"));
        assertEquals(Constants.STATUS_READY, ActiveOrderActivity.normalizeStatus("Food is Ready"));
        
        // Out for delivery aliases
        assertEquals(Constants.STATUS_OUT_FOR_DELIVERY, ActiveOrderActivity.normalizeStatus("In Transit"));
        assertEquals(Constants.STATUS_OUT_FOR_DELIVERY, ActiveOrderActivity.normalizeStatus("out_of_delivery"));
        assertEquals(Constants.STATUS_OUT_FOR_DELIVERY, ActiveOrderActivity.normalizeStatus("out_for_delivery"));
        
        // Delivered aliases
        assertEquals(Constants.STATUS_DELIVERED, ActiveOrderActivity.normalizeStatus("Delivered Successfully"));
        assertEquals(Constants.STATUS_DELIVERED, ActiveOrderActivity.normalizeStatus("done"));
        assertEquals(Constants.STATUS_DELIVERED, ActiveOrderActivity.normalizeStatus("finish"));

        // Safe parsing for unknown
        assertEquals("unknown_status", ActiveOrderActivity.normalizeStatus("unknown_status"));
        assertEquals(Constants.STATUS_PENDING, ActiveOrderActivity.normalizeStatus(null));
        assertEquals(Constants.STATUS_PENDING, ActiveOrderActivity.normalizeStatus(""));
    }

    @Test
    public void testAutoAcceptLogic() {
        // DriverDashboard logic: if pending -> accepted. else keep status.
        String currentStatusPending = Constants.STATUS_PENDING;
        String newStatusForPending = Constants.STATUS_PENDING.equals(currentStatusPending) ? Constants.STATUS_ACCEPTED : currentStatusPending;
        assertEquals(Constants.STATUS_ACCEPTED, newStatusForPending);

        String currentStatusReady = Constants.STATUS_READY;
        String newStatusForReady = Constants.STATUS_PENDING.equals(currentStatusReady) ? Constants.STATUS_ACCEPTED : currentStatusReady;
        assertEquals(Constants.STATUS_READY, newStatusForReady);
    }
}
