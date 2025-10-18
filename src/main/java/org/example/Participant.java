package org.example;

/**
 * Simple classe de données décrivant un participant :
 * - Pseudo (name)
 * - Mise en kamas
 * - Donation (objet éventuel)
 */
public class Participant {
    public static final int DEFAULT_STAKE = 20_000;

    private final javafx.beans.property.StringProperty name;
    private final javafx.beans.property.IntegerProperty kamas;
    private final javafx.beans.property.StringProperty donation;
    private final javafx.beans.property.IntegerProperty stake;
    private final javafx.beans.property.BooleanProperty willReplay;
    private final javafx.beans.property.BooleanProperty paid;

    public Participant(String name, int kamas, String donation) {
        this.name = new javafx.beans.property.SimpleStringProperty(this, "name", name);
        this.kamas = new javafx.beans.property.SimpleIntegerProperty(this, "kamas", Math.max(0, kamas));
        this.donation = new javafx.beans.property.SimpleStringProperty(this, "donation", donation);
        this.stake = new javafx.beans.property.SimpleIntegerProperty(
                this,
                "stake",
                Math.max(0, kamas > 0 ? kamas : DEFAULT_STAKE)
        );
        this.willReplay = new javafx.beans.property.SimpleBooleanProperty(this, "willReplay", true);
        this.paid = new javafx.beans.property.SimpleBooleanProperty(this, "paid", false);
    }

    public String getName() {
        return name.get();
    }

    public void setName(String value) {
        name.set(value);
    }

    public javafx.beans.property.StringProperty nameProperty() {
        return name;
    }

    public int getKamas() {
        return kamas.get();
    }

    public void setKamas(int value) {
        kamas.set(value);
    }

    public javafx.beans.property.IntegerProperty kamasProperty() {
        return kamas;
    }

    public String getDonation() {
        return donation.get();
    }

    public void setDonation(String value) {
        donation.set(value);
    }

    public javafx.beans.property.StringProperty donationProperty() {
        return donation;
    }

    public int getStake() {
        return stake.get();
    }

    public void setStake(int value) {
        stake.set(Math.max(0, value));
    }

    public javafx.beans.property.IntegerProperty stakeProperty() {
        return stake;
    }

    public boolean isWillReplay() {
        return willReplay.get();
    }

    public void setWillReplay(boolean value) {
        willReplay.set(value);
    }

    public javafx.beans.property.BooleanProperty willReplayProperty() {
        return willReplay;
    }

    public boolean isPaid() {
        return paid.get();
    }

    public void setPaid(boolean value) {
        paid.set(value);
    }

    public javafx.beans.property.BooleanProperty paidProperty() {
        return paid;
    }
}
