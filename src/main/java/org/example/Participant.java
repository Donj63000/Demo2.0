package org.example;

/**
 * Simple classe de données décrivant un participant :
 * - Pseudo (name)
 * - Mise en kamas
 * - Donation (objet éventuel)
 */
public class Participant {
    private final javafx.beans.property.StringProperty name;
    private final javafx.beans.property.IntegerProperty kamas;
    private final javafx.beans.property.StringProperty donation;

    public Participant(String name, int kamas, String donation) {
        this.name = new javafx.beans.property.SimpleStringProperty(this, "name", name);
        this.kamas = new javafx.beans.property.SimpleIntegerProperty(this, "kamas", kamas);
        this.donation = new javafx.beans.property.SimpleStringProperty(this, "donation", donation);
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
}
