package com.aircampus.ui;

import com.aircampus.service.PaymentDetails;
import java.time.Clock;
import javafx.scene.control.*;

public final class PaymentForm {
  private final TextField card, expiry;
  private final PasswordField cvv;
  private final CheckBox approve;

  public PaymentForm(Ui.Form form, Clock clock) {
    form.note(
        "SIMULATED PAYMENT ONLY • use the demo card. No banking connection. Card details"
            + " are never stored.");
    card = form.add("Card number", new TextField());
    expiry = form.add("Expiry (MM/YY)", new TextField());
    cvv = form.add("CVV", new PasswordField());
    approve = form.add("Simulated outcome", new CheckBox("Approve payment"));
    approve.setSelected(true);
    Button demo = new Button("Fill demo card");
    demo.setOnAction(
        e -> {
          PaymentDetails details = PaymentDetails.demo(clock);
          card.setText(details.cardNumber());
          expiry.setText(details.expiry());
          cvv.setText(details.cvv());
        });
    form.add("Test data", demo);
  }

  public PaymentDetails details() {
    return new PaymentDetails(
        card.getText(), expiry.getText(), cvv.getText(), approve.isSelected());
  }
}
