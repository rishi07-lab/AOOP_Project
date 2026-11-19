
import java.time.Instant;
import java.util.*;

// 1. Order Status
enum Status {
    DRAFT,
    PLACED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED
}

// 2. Custom Exception
class DomainException extends RuntimeException {
    DomainException(String message) {
        super(message);
    }
}

// 3. Currency
enum Currency {
    USD, EUR, INR
}

// 4. Money Value Object
final class Money {

    private final long minor;
    private final Currency currency;

    private Money(long minor, Currency currency) {
        if (minor < 0) {
            throw new IllegalArgumentException(
                "Money cannot be negative"
            );
        }
        this.minor = minor;
        this.currency = currency;
    }

    static Money of(long minor, Currency currency) {
        return new Money(minor, currency);
    }

    static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    Money plus(Money other) {

        if (currency != other.currency) {
            throw new DomainException(
                "Currency mismatch"
            );
        }

        return new Money(
            Math.addExact(minor, other.minor),
            currency
        );
    }

    Money times(int quantity) {
        return new Money(
            Math.multiplyExact(minor, quantity),
            currency
        );
    }

    long getMinor() {
        return minor;
    }

    Currency getCurrency() {
        return currency;
    }

    @Override
    public String toString() {
        return String.format(
            "%.2f %s", minor / 100.0, currency
        );
    }
}

// 5. Order ID Value Object
final class OrderId {

    private final String value;

    OrderId(String value) {
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

// 6. Product ID Value Object
final class ProductId {

    private final String value;

    ProductId(String value) {
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ProductId)) return false;

        ProductId other = (ProductId) obj;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

// 7. Quantity Value Object
final class Quantity {

    private final int value;

    private Quantity(int value) {
        this.value = value;
    }

    static Quantity of(int value) {
        if (value < 1) {
            throw new IllegalArgumentException(
                "Quantity must be positive"
            );
        }
        return new Quantity(value);
    }

    int value() {
        return value;
    }
}

// 8. Tracking Number Value Object
final class TrackingNumber {

    private final String value;

    TrackingNumber(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "Tracking number cannot be empty"
            );
        }
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}

// 9. Order Line Value Object
final class OrderLine {

    private final ProductId productId;
    private final Money unitPrice;
    private final Quantity quantity;

    OrderLine(
        ProductId productId,
        Money unitPrice,
        Quantity quantity
    ) {
        this.productId = productId;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    OrderLine combine(OrderLine other) {

        if (!productId.equals(other.productId)) {
            throw new DomainException(
                "Different products cannot be combined"
            );
        }

        if (unitPrice.getMinor() != other.unitPrice.getMinor()
            || unitPrice.getCurrency() != other.unitPrice.getCurrency()) {
            throw new DomainException(
                "Product unit price mismatch"
            );
        }

        int newQuantity = Math.addExact(
            quantity.value(),
            other.quantity.value()
        );

        return new OrderLine(
            productId,
            unitPrice,
            Quantity.of(newQuantity)
        );
    }

    Money total() {
        return unitPrice.times(quantity.value());
    }

    ProductId getProductId() {
        return productId;
    }

    @Override
    public String toString() {
        return productId + " | Quantity: " +
               quantity.value() + " | Total: " + total();
    }
}

// 10. Shipment Value Object
final class Shipment {

    private final TrackingNumber trackingNumber;
    private final Instant shippedAt;

    Shipment(TrackingNumber trackingNumber, Instant shippedAt) {
        this.trackingNumber =
            Objects.requireNonNull(trackingNumber);
        this.shippedAt =
            Objects.requireNonNull(shippedAt);
    }

    TrackingNumber getTrackingNumber() {
        return trackingNumber;
    }

    Instant getShippedAt() {
        return shippedAt;
    }
}

// 11. Order Entity / Aggregate Root
final class Order {

    private final OrderId id;

    private Status status = Status.DRAFT;

    private final Currency currency;

    private final Map<ProductId, OrderLine> lines =
        new LinkedHashMap<>();

    private Optional<Shipment> shipment =
        Optional.empty();

    Order(OrderId id, Currency currency) {
        this.id = id;
        this.currency = currency;
    }

    // Check current status
    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new DomainException(
                "Invalid operation. Current status: " + status
            );
        }
    }

    // Add order line
    void addLine(
        ProductId productId,
        Money unitPrice,
        Quantity quantity
    ) {
        requireStatus(Status.DRAFT);

        if (unitPrice.getCurrency() != currency) {
            throw new DomainException(
                "Order currency mismatch"
            );
        }

        OrderLine newLine =
            new OrderLine(productId, unitPrice, quantity);

        lines.merge(
            productId,
            newLine,
            OrderLine::combine
        );
    }

    // Remove order line
    void removeLine(ProductId productId) {
        requireStatus(Status.DRAFT);
        lines.remove(productId);
    }

    // Place order
    void place() {
        requireStatus(Status.DRAFT);

        if (lines.isEmpty()) {
            throw new DomainException(
                "Cannot place an empty order"
            );
        }

        status = Status.PLACED;
    }

    // Payment
    void pay(String paymentReference) {
        requireStatus(Status.PLACED);

        if (paymentReference == null ||
            paymentReference.isBlank()) {
            throw new DomainException(
                "Payment reference is required"
            );
        }

        status = Status.PAID;
    }

    // Ship order
    void ship(TrackingNumber trackingNumber, Instant at) {
        requireStatus(Status.PAID);

        shipment = Optional.of(
            new Shipment(trackingNumber, at)
        );

        status = Status.SHIPPED;
    }

    // Deliver order
    void deliver(Instant at) {
        requireStatus(Status.SHIPPED);

        Objects.requireNonNull(at);

        if (at.isBefore(shipment.get().getShippedAt())) {
            throw new DomainException(
                "Delivery cannot be before shipment"
            );
        }

        status = Status.DELIVERED;
    }

    // Cancel order
    void cancel(String reason) {

        if (status != Status.DRAFT &&
            status != Status.PLACED &&
            status != Status.PAID) {

            throw new DomainException(
                "Order cannot be cancelled in " + status
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new DomainException(
                "Cancellation reason is required"
            );
        }

        status = Status.CANCELLED;
    }

    // Calculate total from order lines
    Money total() {

        Money result = Money.zero(currency);

        for (OrderLine line : lines.values()) {
            result = result.plus(line.total());
        }

        return result;
    }

    // Return read-only order lines
    List<OrderLine> getLines() {
        return List.copyOf(lines.values());
    }

    Status getStatus() {
        return status;
    }

    OrderId getId() {
        return id;
    }

    Optional<Shipment> getShipment() {
        return shipment;
    }
}

// 12. Main Class
public class CaseStudy2 {

    public static void main(String[] args) {

        System.out.println("ORDER MANAGEMENT SYSTEM");
        System.out.println("=======================");

        Order order = new Order(
            new OrderId("ORD101"),
            Currency.USD
        );

        System.out.println("Initial Status: " +
            order.getStatus());

        // Adding products
        order.addLine(
            new ProductId("P101"),
            Money.of(10000, Currency.USD),
            Quantity.of(2)
        );

        order.addLine(
            new ProductId("P102"),
            Money.of(5000, Currency.USD),
            Quantity.of(1)
        );

        // Adding same product again
        order.addLine(
            new ProductId("P101"),
            Money.of(10000, Currency.USD),
            Quantity.of(1)
        );

        System.out.println("\nOrder Lines:");

        for (OrderLine line : order.getLines()) {
            System.out.println(line);
        }

        System.out.println("\nOrder Total: " +
            order.total());

        // Place order
        order.place();

        System.out.println("After Placing: " +
            order.getStatus());

        // Payment
        order.pay("PAY101");

        System.out.println("After Payment: " +
            order.getStatus());

        // Shipping
        order.ship(
            new TrackingNumber("TRACK123"),
            Instant.parse("2026-11-19T04:00:00Z")
        );

        System.out.println("After Shipping: " +
            order.getStatus());

        System.out.println("Tracking Number: " +
            order.getShipment().get().getTrackingNumber());

        // Delivery
        order.deliver(
            Instant.parse("2026-11-20T04:00:00Z")
        );

        System.out.println("After Delivery: " +
            order.getStatus());

        // Test invalid cancellation
        try {
            order.cancel("Customer changed mind");
        }
        catch (DomainException e) {
            System.out.println(
                "Cancellation Error: " + e.getMessage()
            );
        }

        System.out.println("\nFinal Total: " +
            order.total());

        System.out.println("=======================");
        System.out.println("Program Completed Successfully!");
    }
}
