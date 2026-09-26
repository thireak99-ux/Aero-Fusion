package com.aircampus.repository;

import java.sql.*;

@FunctionalInterface
public interface RowMapper<T> {
  T map(ResultSet result) throws SQLException;
}
