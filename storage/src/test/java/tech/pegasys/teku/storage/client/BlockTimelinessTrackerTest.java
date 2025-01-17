/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlockTimelinessTrackerTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final int millisPerSlot = spec.getGenesisSpecConfig().getSecondsPerSlot() * 1000;
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final UInt64 slot = UInt64.ONE;

  private StubTimeProvider timeProvider;
  private Bytes32 blockRoot;
  private SignedBlockAndState signedBlockAndState;
  private BlockTimelinessTracker tracker;

  @BeforeEach
  void setup() {
    signedBlockAndState = dataStructureUtil.randomSignedBlockAndState(slot);
    blockRoot = signedBlockAndState.getBlock().getMessage().getRoot();
    timeProvider =
        StubTimeProvider.withTimeInSeconds(signedBlockAndState.getState().getGenesisTime());
    tracker = new BlockTimelinessTracker(spec, recentChainData, () -> timeProvider);

    when(recentChainData.getGenesisTime())
        .thenReturn(signedBlockAndState.getState().getGenesisTime());
    when(recentChainData.getGenesisTimeMillis())
        .thenReturn(signedBlockAndState.getState().getGenesisTime().times(1000));
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.of(UInt64.ONE));
  }

  @Test
  void blockTimeliness_shouldReportTimelinessIfSet() {
    final UInt64 computedTime = computeTime(slot, 500);

    tracker.setBlockTimelinessFromArrivalTime(signedBlockAndState.getBlock(), computedTime);
    assertThat(tracker.isBlockTimely(blockRoot)).contains(true);
    assertThat(tracker.isBlockLate(blockRoot)).isFalse();
  }

  @Test
  void blockTimeliness_shouldSetTimelinessOnce() {
    final UInt64 computedTime = computeTime(slot, 500);

    tracker.setBlockTimelinessFromArrivalTime(signedBlockAndState.getBlock(), computedTime);
    // The block would be late in the tracker if this set is not ignored, but it should be ignored
    // because its already been set
    tracker.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computedTime.plus(3000));
    assertThat(tracker.isBlockTimely(blockRoot)).contains(true);
    assertThat(tracker.isBlockLate(blockRoot)).isFalse();
  }

  @Test
  void blockTimeliness_shouldReportFalseIfLate() {
    final UInt64 computedTime = computeTime(slot, 2100);

    tracker.setBlockTimelinessFromArrivalTime(signedBlockAndState.getBlock(), computedTime);
    assertThat(tracker.isBlockTimely(blockRoot)).contains(false);
    assertThat(tracker.isBlockLate(blockRoot)).isTrue();
  }

  @Test
  void blockTimeliness_shouldReportFalseIfAtLimit() {
    final UInt64 computedTime = computeTime(slot, 2000);

    tracker.setBlockTimelinessFromArrivalTime(signedBlockAndState.getBlock(), computedTime);
    assertThat(tracker.isBlockTimely(blockRoot)).contains(false);
    assertThat(tracker.isBlockLate(blockRoot)).isTrue();
  }

  @Test
  void blockTimeliness_ifBlockFromFuture() {
    final UInt64 computedTime = computeTime(slot, 2100);

    tracker.setBlockTimelinessFromArrivalTime(
        dataStructureUtil.randomSignedBeaconBlock(0), computedTime);
    assertThat(tracker.isBlockTimely(blockRoot)).isEmpty();
    assertThat(tracker.isBlockLate(blockRoot)).isFalse();
  }

  @Test
  void blockTimeliness_shouldReportEmptyIfNotSet() {
    assertThat(tracker.isBlockTimely(blockRoot)).isEmpty();
    assertThat(tracker.isBlockLate(blockRoot)).isFalse();
  }

  @Test
  void isProposingOnTime_shouldDetectBeforeSlotStartAsOk() {
    // Advance time to 500ms before slot start
    timeProvider.advanceTimeByMillis(millisPerSlot - 500);
    assertThat(tracker.isProposingOnTime(slot)).isTrue();
  }

  @Test
  void isProposingOnTime_shouldDetectSlotStartAsOnTime() {
    // Advance time by 1 slot, leaving us at exactly slot time
    timeProvider.advanceTimeByMillis(millisPerSlot);
    assertThat(tracker.isProposingOnTime(slot)).isTrue();
  }

  @Test
  void isProposingOnTime_shouldDetectLateIfAttestationsDue() {
    // attestation is due 2 seconds into slot
    timeProvider.advanceTimeByMillis(millisPerSlot + 2000);
    assertThat(tracker.isProposingOnTime(slot)).isFalse();
  }

  @Test
  void isProposingOnTime_shouldDetectOnTimeBeforeCutoff() {
    /// 999 ms into slot, cutoff is 1000ms
    timeProvider.advanceTimeByMillis(millisPerSlot + 999);
    assertThat(tracker.isProposingOnTime(slot)).isTrue();
  }

  @Test
  void isProposingOnTime_shouldDetectLateIfHalfWayToAttestationDue() {
    timeProvider.advanceTimeByMillis(millisPerSlot + 1000);
    assertThat(tracker.isProposingOnTime(slot)).isFalse();
  }

  private UInt64 computeTime(final UInt64 slot, final long timeIntoSlot) {
    return timeProvider.getTimeInMillis().plus(slot.times(millisPerSlot)).plus(timeIntoSlot);
  }
}
