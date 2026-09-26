package com.aircampus.ui;

import com.aircampus.AppContext;
import com.aircampus.model.Session;
import javafx.stage.Stage;

public interface ScreenController {
  void attach(AppContext context, Session session, Stage stage);

  default void dispose() {}
}
