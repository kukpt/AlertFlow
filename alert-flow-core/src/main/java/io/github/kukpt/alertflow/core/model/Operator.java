package io.github.kukpt.alertflow.core.model;

public enum Operator {
    GT(">"),
    GTE(">="),
    LT("<"),
    LTE("<="),
    EQ("=="),
    NE("!=");

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public boolean test(double left, double right) {
        switch (this) {
            case GT:
                return left > right;
            case GTE:
                return left >= right;
            case LT:
                return left < right;
            case LTE:
                return left <= right;
            case EQ:
                return Double.compare(left, right) == 0;
            case NE:
                return Double.compare(left, right) != 0;
            default:
                throw new IllegalStateException("Unsupported operator: " + this);
        }
    }

    public static Operator fromSymbol(String symbol) {
        for (Operator operator : values()) {
            if (operator.symbol.equals(symbol)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("Unsupported operator: " + symbol);
    }
}
