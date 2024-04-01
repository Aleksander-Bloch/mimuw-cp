package cp2023.tests;

public record TransferParams(int componentId, int sourceDeviceId, int destinationDeviceId,
                             long waitBeforeTransferStartDuration, long waitBeforePrepareDuration,
                             long transferPerformDuration) {
}