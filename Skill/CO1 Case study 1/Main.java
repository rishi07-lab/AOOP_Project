
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// 1. Currency enum
enum Currency {
    EUR(2),
    USD(2),
    JPY(0),
    KWD(3);

    final int minorUnits;

    Currency(int minorUnits) {
        this.minorUnits = minorUnits;
    }
}

// 2. Custom exception for currency mismatch
class CurrencyMismatch extends RuntimeException {
    CurrencyMismatch() {
        super("Cannot operate on different currencies!");
    }
}

// 3. Money Value Object
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
        this.currency = Objects.requireNonNull(currency);
    }

    // Creates Money from decimal string
    static Money of(String decimal, Currency c) {

        Objects.requireNonNull(c);

        BigDecimal amount = new BigDecimal(decimal);

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                "Negative price is not allowed"
            );
        }

        long minor = amount
                .movePointRight(c.minorUnits)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();

        return new Money(minor, c);
    }

    // Addition
    Money plus(Money o) {
        requireSame(o);

        return new Money(
            Math.addExact(minor, o.minor),
            currency
        );
    }

    // Multiplication by quantity
    Money times(Quantity q) {

        return new Money(
            Math.multiplyExact(minor, q.value()),
            currency
        );
    }

    // Applying discount
    Money minus(Percentage p) {

        BigDecimal result = BigDecimal.valueOf(minor)
                .multiply(
                    BigDecimal.valueOf(
                        10000L - p.basisPoints()
                    )
                )
                .divide(
                    BigDecimal.valueOf(10000),
                    0,
                    RoundingMode.HALF_EVEN
                );

        return new Money(result.longValueExact(), currency);
    }

    // Currency validation
    private void requireSame(Money o) {

        if (o == null || currency != o.currency) {
            throw new CurrencyMismatch();
        }
    }

    long minor() {
        return minor;
    }

    Currency currency() {
        return currency;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) return true;

        if (!(obj instanceof Money)) return false;

        Money other = (Money) obj;

        return minor == other.minor &&
               currency == other.currency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minor, currency);
    }

    @Override
    public String toString() {

        return BigDecimal.valueOf(minor, currency.minorUnits)
                .toPlainString() + " " + currency;
    }
}

// 4. Percentage Value Object
final class Percentage {

    private final int basisPoints;

    private Percentage(int basisPoints) {
        this.basisPoints = basisPoints;
    }

    static Percentage ofBasisPoints(int bp) {

        if (bp < 0 || bp > 10000) {
            throw new IllegalArgumentException(
                "Percentage must be between 0 and 100"
            );
        }

        return new Percentage(bp);
    }

    // Example: 15 means 15%
    static Percentage ofPercent(String percent) {

        int bp = new BigDecimal(percent)
                .multiply(BigDecimal.valueOf(100))
                .intValueExact();

        return ofBasisPoints(bp);
    }

    int basisPoints() {
        return basisPoints;
    }

    // Applies discount to Money
    Money applyTo(Money amount) {
        return amount.minus(this);
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) return true;

        if (!(obj instanceof Percentage)) return false;

        Percentage other = (Percentage) obj;

        return basisPoints == other.basisPoints;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(basisPoints);
    }

    @Override
    public String toString() {
        return BigDecimal.valueOf(basisPoints, 2)
                .toPlainString() + "%";
    }
}

// 5. Quantity Value Object
final class Quantity {

    private final int value;

    private Quantity(int value) {
        this.value = value;
    }

    static Quantity of(int v) {

        if (v < 1) {
            throw new IllegalArgumentException(
                "Quantity must be positive"
            );
        }

        return new Quantity(v);
    }

    int value() {
        return value;
    }
}

// 6. Product Entity
class Product {

    private final String sku;
    private String name;
    private Money price;

    Product(String sku, String name, Money price) {

        this.sku = Objects.requireNonNull(sku);
        this.name = Objects.requireNonNull(name);
        this.price = Objects.requireNonNull(price);
    }

    String getSku() {
        return sku;
    }

    Money getPrice() {
        return price;
    }

    void updatePrice(Money newPrice) {
        this.price = Objects.requireNonNull(newPrice);
    }
}

// 7. Cart Item
class CartItem {

    Product product;
    Quantity quantity;

    CartItem(Product product, Quantity quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    Money getTotal() {
        return product.getPrice().times(quantity);
    }
}

// 8. Cart Entity / Aggregate Root
class Cart {

    private final String cartId;
    private final List<CartItem> items = new ArrayList<>();

    Cart(String cartId) {
        this.cartId = cartId;
    }

    void addItem(CartItem item) {
        items.add(item);
    }

    Money getTotal() {

        if (items.isEmpty()) {
            return Money.of("0", Currency.USD);
        }

        Money total = items.get(0).getTotal();

        for (int i = 1; i < items.size(); i++) {
            total = total.plus(items.get(i).getTotal());
        }

        return total;
    }
}

// 9. Main class
public class Main {

    public static void main(String[] args) {

        System.out.println("MONEY AND PRICING SYSTEM");
        System.out.println("------------------------");

        // Creating money
        Money price = Money.of("100.00", Currency.USD);

        System.out.println("Original Price: " + price);

        // Creating percentage
        Percentage discount = Percentage.ofPercent("15");

        System.out.println("Discount: " + discount);

        // Applying discount
        Money finalPrice = discount.applyTo(price);

        System.out.println("Discounted Price: " + finalPrice);

        // Quantity multiplication
        Quantity quantity = Quantity.of(2);

        Money total = finalPrice.times(quantity);

        System.out.println("Total for 2 items: " + total);

        // Adding same currency
        Money price1 = Money.of("50.00", Currency.USD);
        Money price2 = Money.of("25.00", Currency.USD);

        System.out.println(
            "Addition: " + price1.plus(price2)
        );

        // Currency mismatch test
        try {
            Money euro = Money.of("50.00", Currency.EUR);
            price1.plus(euro);

        } catch (CurrencyMismatch e) {
            System.out.println("Currency Error: " + e.getMessage());
        }

        // Invalid discount test
        try {
            Percentage.ofBasisPoints(25000);

        } catch (IllegalArgumentException e) {
            System.out.println("Discount Error: " + e.getMessage());
        }

        // Invalid quantity test
        try {
            Quantity.of(0);

        } catch (IllegalArgumentException e) {
            System.out.println("Quantity Error: " + e.getMessage());
        }

        // Cart demonstration
        Product product = new Product(
            "P101",
            "Laptop",
            Money.of("500.00", Currency.USD)
        );

        Cart cart = new Cart("C101");

        cart.addItem(
            new CartItem(product, Quantity.of(2))
        );

        System.out.println("Cart Total: " + cart.getTotal());

        System.out.println("------------------------");
        System.out.println("Program completed successfully!");
    }
}
