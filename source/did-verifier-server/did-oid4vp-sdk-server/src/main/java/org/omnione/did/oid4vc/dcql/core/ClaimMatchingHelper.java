/*
 * Copyright 2026 OmniOne.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnione.did.oid4vc.dcql.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.omnione.did.oid4vc.dcql.datamodel.DCQLQuery;

/**
 * Shared helper for path-based claim matching and condition evaluation.
 * Used by JSON-based credential adapters (SD-JWT, OpenDID VC)
 * and as a fallback for mdoc adapters.
 */
@Slf4j
public class ClaimMatchingHelper {

  private ClaimMatchingHelper() {
  }

  /**
   * Extracts matching claim names from claims map using path-based navigation.
   * Used by JSON-based credential formats (SD-JWT, W3C VC).
   */
  public static Set<String> extractMatchingClaimsByPath(Map<String, Object> allClaims,
      List<DCQLQuery.ClaimQuery> claimQueries) {

    Set<String> matchingClaims = new HashSet<>();

    if (claimQueries == null || claimQueries.isEmpty()) {
      matchingClaims.addAll(allClaims.keySet());
      return matchingClaims;
    }

    for (DCQLQuery.ClaimQuery claimQuery : claimQueries) {
      List<Object> path = claimQuery.getPath();
      if (path == null || path.isEmpty()) {
        continue;
      }
      processPathAndCollectClaims(allClaims, path, claimQuery, matchingClaims);
    }

    return matchingClaims;
  }

  /**
   * Checks if an actual value meets the conditions specified in a ClaimQuery.
   */
  public static boolean meetsConditions(DCQLQuery.ClaimQuery claimQuery, Object actualValue) {
    if (actualValue == null) {
      return false;
    }

    if (claimQuery.getValues() != null && !claimQuery.getValues().isEmpty()) {
      if (!claimQuery.getValues().contains(actualValue)) {
        return false;
      }
    }

    if (claimQuery.getValue() != null) {
      if (!claimQuery.getValue().equals(actualValue)) {
        return false;
      }
    }

    if (claimQuery.getMin() != null) {
      if (!checkMinCondition(actualValue, claimQuery.getMin())) {
        return false;
      }
    }

    if (claimQuery.getMax() != null) {
      if (!checkMaxCondition(actualValue, claimQuery.getMax())) {
        return false;
      }
    }

    return true;
  }

  // ========== Private Methods ==========

  private static void processPathAndCollectClaims(Map<String, Object> allClaims,
      List<Object> path, DCQLQuery.ClaimQuery claimQuery, Set<String> matchingClaims) {

    if (path.isEmpty() || !(path.get(0) instanceof String)) {
      return;
    }

    String topLevelClaim = (String) path.get(0);
    Object rootValue = allClaims.get(topLevelClaim);

    if (rootValue == null) {
      return;
    }

    if (path.size() == 1) {
      if (meetsConditions(claimQuery, rootValue)) {
        matchingClaims.add(topLevelClaim);
      }
      return;
    }

    List<Object> remainingPath = new ArrayList<>(path.subList(1, path.size()));
    collectMatchingValuesFromPath(rootValue, remainingPath, claimQuery, topLevelClaim, matchingClaims);
  }

  private static void collectMatchingValuesFromPath(Object current, List<Object> remainingPath,
      DCQLQuery.ClaimQuery claimQuery, String claimNamePrefix, Set<String> matchingClaims) {

    if (remainingPath.isEmpty()) {
      if (meetsConditions(claimQuery, current)) {
        matchingClaims.add(claimNamePrefix);
      }
      return;
    }

    Object nextPathElement = remainingPath.get(0);
    List<Object> nextRemaining = new ArrayList<>(remainingPath.subList(1, remainingPath.size()));

    if (nextPathElement == null) {
      if (!(current instanceof List)) {
        return;
      }
      List<?> currentList = (List<?>) current;
      for (int i = 0; i < currentList.size(); i++) {
        collectMatchingValuesFromPath(currentList.get(i), nextRemaining, claimQuery,
            claimNamePrefix + "[" + i + "]", matchingClaims);
      }
      return;
    }

    if (nextPathElement instanceof Integer) {
      if (!(current instanceof List)) {
        return;
      }
      int idx = (Integer) nextPathElement;
      List<?> currentList = (List<?>) current;
      if (idx < 0 || idx >= currentList.size()) {
        return;
      }
      collectMatchingValuesFromPath(currentList.get(idx), nextRemaining, claimQuery,
          claimNamePrefix + "[" + idx + "]", matchingClaims);
      return;
    }

    if (nextPathElement instanceof String) {
      if (!(current instanceof Map)) {
        return;
      }
      Map<?, ?> currentMap = (Map<?, ?>) current;
      String key = (String) nextPathElement;
      Object nextValue = currentMap.get(key);
      if (nextValue == null) {
        return;
      }
      collectMatchingValuesFromPath(nextValue, nextRemaining, claimQuery,
          claimNamePrefix + "." + key, matchingClaims);
    }
  }

  private static boolean checkMinCondition(Object actualValue, Object minValue) {
    if (actualValue instanceof Number && minValue instanceof Number) {
      return ((Number) actualValue).doubleValue() >= ((Number) minValue).doubleValue();
    }
    if (actualValue instanceof String && minValue instanceof String) {
      return ((String) actualValue).compareTo((String) minValue) >= 0;
    }
    return true;
  }

  private static boolean checkMaxCondition(Object actualValue, Object maxValue) {
    if (actualValue instanceof Number && maxValue instanceof Number) {
      return ((Number) actualValue).doubleValue() <= ((Number) maxValue).doubleValue();
    }
    if (actualValue instanceof String && maxValue instanceof String) {
      return ((String) actualValue).compareTo((String) maxValue) <= 0;
    }
    return true;
  }
}
