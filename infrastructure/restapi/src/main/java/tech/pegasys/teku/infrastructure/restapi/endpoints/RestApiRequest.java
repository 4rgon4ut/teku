/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import static tech.pegasys.teku.infrastructure.json.JsonUtil.JSON_CONTENT_TYPE;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.BadRequest.BAD_REQUEST_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequestBodyException;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;

public class RestApiRequest {
  private final Context context;
  private final EndpointMetadata metadata;
  private final Map<String, String> pathParamMap;
  private final Map<String, List<String>> queryParamMap;

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  public <T> T getRequestBody() throws JsonProcessingException {
    DeserializableTypeDefinition<T> bodySchema =
        (DeserializableTypeDefinition<T>) metadata.getRequestBodyType();

    final String body = context.body();
    final T result = JsonUtil.parse(body, bodySchema);
    if (result == null) {
      throw new MissingRequestBodyException();
    }
    return result;
  }

  public RestApiRequest(final Context context, final EndpointMetadata metadata) {
    this.context = context;
    this.metadata = metadata;
    this.pathParamMap = context.pathParamMap();
    this.queryParamMap = context.queryParamMap();
  }

  public void respondOk(final Object response) throws JsonProcessingException {
    respond(HttpStatusCodes.SC_OK, JSON_CONTENT_TYPE, response);
  }

  public void respondOk(final Object response, final CacheLength cacheLength)
      throws JsonProcessingException {
    context.header(Header.CACHE_CONTROL, cacheLength.getHttpHeaderValue());
    respond(HttpStatusCodes.SC_OK, JSON_CONTENT_TYPE, response);
  }

  public void respondError(final int statusCode, final String message)
      throws JsonProcessingException {
    respond(statusCode, JSON_CONTENT_TYPE, new HttpErrorResponse(statusCode, message));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void respond(final int statusCode, final String contentType, final Object response)
      throws JsonProcessingException {
    final SerializableTypeDefinition type = metadata.getResponseType(statusCode, contentType);
    context.status(statusCode);
    context.result(JsonUtil.serialize(response, type));
  }

  /** This is only used when intending to return status code without a response body */
  public void respondWithCode(final int statusCode) {
    context.status(statusCode);
  }

  public String getPathParam(final String pathParameter) {
    return pathParamMap.get(pathParameter);
  }

  public int getQueryParamAsInt(final String queryParameter) {
    return SingleQueryParameterUtils.getParameterValueAsInt(queryParamMap, queryParameter);
  }

  public Optional<Integer> getQueryParamAsOptionalInteger(final String queryParameter) {
    return SingleQueryParameterUtils.getParameterValueAsIntegerIfPresent(
        queryParamMap, queryParameter);
  }

  public <T> void handleOptionalResult(
      SafeFuture<Optional<T>> future, ResultProcessor<T> resultProcessor, final int missingStatus) {
    context.future(
        future.thenApplyChecked(
            result -> {
              if (result.isPresent()) {
                return resultProcessor.process(context, result.get()).orElse(null);
              } else {
                context.status(missingStatus);
                return JsonUtil.serialize(
                    new BadRequest(missingStatus, "Not found"), BAD_REQUEST_TYPE);
              }
            }));
  }

  @FunctionalInterface
  public interface ResultProcessor<T> {
    // Process result, returning an optional serialized response
    Optional<String> process(Context context, T result) throws Exception;
  }
}
