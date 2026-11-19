
class DraftOrder {
    int orderId;
    double amount;

    DraftOrder(int orderId, double amount) {
        this.orderId = orderId;
        this.amount = amount;
    }

    PlacedOrder placeOrder() {
        System.out.println("Order placed successfully.");
        return new PlacedOrder(orderId, amount);
    }

    CancelledOrder cancelOrder() {
        System.out.println("Order cancelled.");
        return new CancelledOrder(orderId);
    }
}

class PlacedOrder {
    int orderId;
    double amount;

    PlacedOrder(int orderId, double amount) {
        this.orderId = orderId;
        this.amount = amount;
    }

    PaidOrder makePayment() {
        System.out.println("Payment completed: Rs. " + amount);
        return new PaidOrder(orderId, amount);
    }

    CancelledOrder cancelOrder() {
        System.out.println("Order cancelled.");
        return new CancelledOrder(orderId);
    }
}

class PaidOrder {
    int orderId;
    double amount;

    PaidOrder(int orderId, double amount) {
        this.orderId = orderId;
        this.amount = amount;
    }

    ShippedOrder shipOrder() {
        System.out.println("Order shipped successfully.");
        return new ShippedOrder(orderId);
    }
}

class ShippedOrder {
    int orderId;

    ShippedOrder(int orderId) {
        this.orderId = orderId;
    }

    DeliveredOrder deliverOrder() {
        System.out.println("Order delivered successfully.");
        return new DeliveredOrder(orderId);
    }
}

class DeliveredOrder {
    int orderId;

    DeliveredOrder(int orderId) {
        this.orderId = orderId;
    }

    void showStatus() {
        System.out.println("Order Status: DELIVERED");
    }
}

class CancelledOrder {
    int orderId;

    CancelledOrder(int orderId) {
        this.orderId = orderId;
    }

    void showStatus() {
        System.out.println("Order Status: CANCELLED");
    }
}

public class ModelB {
    public static void main(String[] args) {

        DraftOrder draft = new DraftOrder(101, 2500);

        System.out.println("Order ID: 101");

        PlacedOrder placed = draft.placeOrder();

        PaidOrder paid = placed.makePayment();

        ShippedOrder shipped = paid.shipOrder();

        DeliveredOrder delivered = shipped.deliverOrder();

        delivered.showStatus();
    }
}
