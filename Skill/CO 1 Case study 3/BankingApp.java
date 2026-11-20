
import java.time.Instant;
import java.util.*;

// Money Value Object
final class Money {
    private final long paise;

    private Money(long paise) {
        this.paise = paise;
    }

    public static Money ofPaise(long paise) {
        if (paise < 0) {
            throw new IllegalArgumentException(
                "Money cannot be negative"
            );
        }
        return new Money(paise);
    }

    public static Money rupees(long rupees) {
        return ofPaise(Math.multiplyExact(rupees, 100));
    }

    public long paise() {
        return paise;
    }

    public boolean isZero() {
        return paise == 0;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Money m && m.paise == paise;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(paise);
    }

    @Override
    public String toString() {
        return String.format(
            Locale.ROOT, "Rs. %d.%02d",
            paise / 100, paise % 100
        );
    }
}

// Account Number Value Object
final class AccountNumber {
    private final String digits;

    private AccountNumber(String digits) {
        this.digits = digits;
    }

    public static AccountNumber of(String digits) {
        if (digits == null || !digits.matches("\\d{12}")) {
            throw new IllegalArgumentException(
                "Account number must contain 12 digits"
            );
        }
        return new AccountNumber(digits);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AccountNumber a
            && a.digits.equals(digits);
    }

    @Override
    public int hashCode() {
        return digits.hashCode();
    }

    @Override
    public String toString() {
        return digits;
    }
}

// Account Status
enum AccountStatus {
    ACTIVE, FROZEN, CLOSED
}

// Ledger Entry Type
enum EntryType {
    DEPOSIT, WITHDRAWAL, TRANSFER_IN, TRANSFER_OUT
}

// Immutable Ledger Entry
record LedgerEntry(
    UUID id,
    AccountNumber accountNumber,
    EntryType type,
    Money amount,
    long balanceAfter,
    Instant at,
    String reference
) {}

// Custom Exceptions
class AccountException extends RuntimeException {
    public AccountException(String message) {
        super(message);
    }
}

class InsufficientFundsException extends AccountException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

// Bank Account Entity
final class Account {
    private final AccountNumber number;
    private final Money overdraftLimit;

    private AccountStatus status = AccountStatus.ACTIVE;
    private long balance = 0;

    private final List<LedgerEntry> ledger =
        new ArrayList<>();

    public Account(
        AccountNumber number,
        Money overdraftLimit
    ) {
        this.number = Objects.requireNonNull(number);
        this.overdraftLimit =
            Objects.requireNonNull(overdraftLimit);
    }

    // Deposit Money
    public synchronized void deposit(
        Money amount,
        String reference
    ) {
        requirePositive(amount);

        if (status == AccountStatus.CLOSED) {
            throw new AccountException(
                "Account is closed"
            );
        }

        long newBalance =
            Math.addExact(balance, amount.paise());

        record(
            EntryType.DEPOSIT,
            amount,
            newBalance,
            reference
        );
    }

    // Withdraw Money
    public synchronized void withdraw(
        Money amount,
        String reference
    ) {
        validateWithdrawal(amount);

        long newBalance =
            Math.subtractExact(balance, amount.paise());

        record(
            EntryType.WITHDRAWAL,
            amount,
            newBalance,
            reference
        );
    }

    // Validate Withdrawal
    synchronized void validateWithdrawal(Money amount) {
        requirePositive(amount);

        if (status != AccountStatus.ACTIVE) {
            throw new AccountException(
                "Cannot withdraw, account is " + status
            );
        }

        long newBalance;
        try {
            newBalance =
                Math.subtractExact(balance, amount.paise());
        } catch (ArithmeticException e) {
            throw new InsufficientFundsException(
                "Overdraft limit exceeded"
            );
        }

        if (newBalance < -overdraftLimit.paise()) {
            throw new InsufficientFundsException(
                "Overdraft limit exceeded"
            );
        }
    }

    // Validate Deposit for Transfer
    synchronized void validateTransferCredit(Money amount) {
        requirePositive(amount);

        if (status == AccountStatus.CLOSED) {
            throw new AccountException(
                "Destination account is closed"
            );
        }

        try {
            Math.addExact(balance, amount.paise());
        } catch (ArithmeticException e) {
            throw new AccountException(
                "Account balance limit exceeded"
            );
        }
    }

    // Record Transfer Debit
    synchronized void transferDebit(
        Money amount,
        String reference
    ) {
        long newBalance =
            Math.subtractExact(balance, amount.paise());

        record(
            EntryType.TRANSFER_OUT,
            amount,
            newBalance,
            reference
        );
    }

    // Record Transfer Credit
    synchronized void transferCredit(
        Money amount,
        String reference
    ) {
        long newBalance =
            Math.addExact(balance, amount.paise());

        record(
            EntryType.TRANSFER_IN,
            amount,
            newBalance,
            reference
        );
    }

    // Freeze Account
    public synchronized void freeze() {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountException(
                "Only an active account can be frozen"
            );
        }

        status = AccountStatus.FROZEN;
    }

    // Unfreeze Account
    public synchronized void unfreeze() {
        if (status != AccountStatus.FROZEN) {
            throw new AccountException(
                "Account is not frozen"
            );
        }

        status = AccountStatus.ACTIVE;
    }

    // Close Account
    public synchronized void close() {
        if (status == AccountStatus.CLOSED) {
            throw new AccountException(
                "Account is already closed"
            );
        }

        if (balance != 0) {
            throw new AccountException(
                "Balance must be zero to close"
            );
        }

        status = AccountStatus.CLOSED;
    }

    // Only method that changes balance and ledger together
    private void record(
        EntryType type,
        Money amount,
        long newBalance,
        String reference
    ) {
        ledger.add(
            new LedgerEntry(
                UUID.randomUUID(),
                number,
                type,
                amount,
                newBalance,
                Instant.now(),
                reference
            )
        );

        balance = newBalance;
    }

    private static void requirePositive(Money amount) {
        if (amount == null || amount.isZero()) {
            throw new IllegalArgumentException(
                "Amount must be greater than zero"
            );
        }
    }

    public synchronized long balanceInPaise() {
        return balance;
    }

    public synchronized AccountStatus status() {
        return status;
    }

    public AccountNumber number() {
        return number;
    }

    public synchronized List<LedgerEntry> ledger() {
        return List.copyOf(ledger);
    }

    public synchronized void displayBalance() {
        System.out.println(
            number + " Balance: " +
            Money.ofPaise(balance)
        );
    }
}

// Fund Transfer Status
enum TransferStatus {
    COMPLETED
}

// Fund Transfer Entity
record FundTransfer(
    UUID id,
    AccountNumber from,
    AccountNumber to,
    Money amount,
    TransferStatus status,
    Instant createdAt
) {}

// Transfer Service
class TransferService {
    private final Map<UUID, FundTransfer> transfers =
        new HashMap<>();

    // Transfer Money Safely
    public synchronized void transfer(
        UUID id,
        Account source,
        Account destination,
        Money amount
    ) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(source);
        Objects.requireNonNull(destination);

        // Prevent duplicate transfers
        if (transfers.containsKey(id)) {
            System.out.println(
                "Transfer already processed. Duplicate ignored."
            );
            return;
        }

        if (source.number().equals(destination.number())) {
            throw new AccountException(
                "Cannot transfer to the same account"
            );
        }

        // Lock accounts in a consistent order
        Account first = source.number().toString()
            .compareTo(destination.number().toString()) < 0
            ? source : destination;

        Account second = first == source
            ? destination : source;

        synchronized (first) {
            synchronized (second) {

                // Validate both operations before changing balances
                source.validateWithdrawal(amount);
                destination.validateTransferCredit(amount);

                // Perform both operations
                source.transferDebit(amount, id.toString());

                destination.transferCredit(
                    amount, id.toString()
                );

                // Save completed transfer
                FundTransfer transfer = new FundTransfer(
                    id,
                    source.number(),
                    destination.number(),
                    amount,
                    TransferStatus.COMPLETED,
                    Instant.now()
                );

                transfers.put(id, transfer);

                System.out.println(
                    "Transfer completed successfully!"
                );
            }
        }
    }
}

// Main Class
public class BankingApp {
    public static void main(String[] args) {

        Account accountA = new Account(
            AccountNumber.of("123456789012"),
            Money.rupees(2000)
        );

        Account accountB = new Account(
            AccountNumber.of("987654321098"),
            Money.rupees(1000)
        );

        TransferService service = new TransferService();

        System.out.println("BANK ACCOUNT SYSTEM");
        System.out.println("-------------------");

        // Initial Deposits
        accountA.deposit(
            Money.rupees(10000), "Initial deposit"
        );

        accountB.deposit(
            Money.rupees(2000), "Initial deposit"
        );

        System.out.println("\nBefore Transfer:");
        accountA.displayBalance();
        accountB.displayBalance();

        // Transfer Rs. 5000
        System.out.println("\nTransferring Rs. 5000...");

        UUID transferId = UUID.randomUUID();

        service.transfer(
            transferId,
            accountA,
            accountB,
            Money.rupees(5000)
        );

        System.out.println("\nAfter Transfer:");
        accountA.displayBalance();
        accountB.displayBalance();

        // Display Ledger
        System.out.println("\nAccount A Ledger:");

        for (LedgerEntry entry : accountA.ledger()) {
            System.out.println(
                entry.type() + " | " +
                entry.amount() + " | Balance: " +
                Money.ofPaise(entry.balanceAfter())
            );
        }

        System.out.println("\nAccount B Ledger:");

        for (LedgerEntry entry : accountB.ledger()) {
            System.out.println(
                entry.type() + " | " +
                entry.amount() + " | Balance: " +
                Money.ofPaise(entry.balanceAfter())
            );
        }

        // Test Frozen Account
        System.out.println("\nTesting Frozen Account:");

        accountA.freeze();

        try {
            accountA.withdraw(
                Money.rupees(500), "Test withdrawal"
            );
        } catch (AccountException e) {
            System.out.println("Error: " + e.getMessage());
        }

        accountA.unfreeze();

        System.out.println(
            "Account Status: " + accountA.status()
        );
    }
}
