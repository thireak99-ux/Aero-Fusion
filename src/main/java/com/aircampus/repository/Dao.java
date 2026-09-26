package com.aircampus.repository;

import java.util.*;

public interface Dao<T, K> {
  Optional<T> findById(K id);

  List<T> findAll();
}
