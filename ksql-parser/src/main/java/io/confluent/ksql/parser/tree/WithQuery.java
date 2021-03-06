/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License; you may not use this file
 * except in compliance with the License.  You may obtain a copy of the License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.parser.tree;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class WithQuery
    extends Node {

  private final String name;
  private final Query query;
  private final Optional<List<String>> columnNames;

  public WithQuery(final String name, final Query query, final Optional<List<String>> columnNames) {
    this(Optional.empty(), name, query, columnNames);
  }

  public WithQuery(final NodeLocation location, final String name, final Query query,
                   final Optional<List<String>> columnNames) {
    this(Optional.of(location), name, query, columnNames);
  }

  private WithQuery(final Optional<NodeLocation> location, final String name, final Query query,
                    final Optional<List<String>> columnNames) {
    super(location);
    this.name = QualifiedName.of(requireNonNull(name, "name is null")).getParts().get(0);
    this.query = requireNonNull(query, "query is null");
    this.columnNames = requireNonNull(columnNames, "columnNames is null");
  }

  public String getName() {
    return name;
  }

  public Query getQuery() {
    return query;
  }

  public Optional<List<String>> getColumnNames() {
    return columnNames;
  }

  @Override
  public <R, C> R accept(final AstVisitor<R, C> visitor, final C context) {
    return visitor.visitWithQuery(this, context);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("name", name)
        .add("query", query)
        .add("columnNames", columnNames)
        .omitNullValues()
        .toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, query, columnNames);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if ((obj == null) || (getClass() != obj.getClass())) {
      return false;
    }
    final WithQuery o = (WithQuery) obj;
    return Objects.equals(name, o.name)
           && Objects.equals(query, o.query)
           && Objects.equals(columnNames, o.columnNames);
  }
}
