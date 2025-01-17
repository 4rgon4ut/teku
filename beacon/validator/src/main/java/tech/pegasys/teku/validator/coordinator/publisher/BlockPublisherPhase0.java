/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.validator.coordinator.publisher;

import java.util.List;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

public class BlockPublisherPhase0 extends AbstractBlockPublisher {
  private final BlockGossipChannel blockGossipChannel;

  public BlockPublisherPhase0(
      final BlockFactory blockFactory,
      final BlockGossipChannel blockGossipChannel,
      final BlockImportChannel blockImportChannel,
      final PerformanceTracker performanceTracker,
      final DutyMetrics dutyMetrics) {
    super(blockFactory, blockImportChannel, performanceTracker, dutyMetrics);
    this.blockGossipChannel = blockGossipChannel;
  }

  @Override
  SafeFuture<BlockImportAndBroadcastValidationResults> importBlockAndBlobSidecars(
      final SignedBeaconBlock block,
      final List<BlobSidecar> blobSidecars,
      final BroadcastValidationLevel broadcastValidationLevel) {
    return blockImportChannel.importBlock(block, broadcastValidationLevel);
  }

  @Override
  void publishBlockAndBlobSidecars(
      final SignedBeaconBlock block, final List<BlobSidecar> blobSidecars) {
    blockGossipChannel.publishBlock(block);
  }
}
