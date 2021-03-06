package tachyon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.thrift.TException;

/**
 * Handle local block locking. This class is NOT thread safe.
 */
public class BlocksLocker {
  // All Blocks has been locked.
  private Map<Long, Set<Integer>> mLockedBlockIds = new HashMap<Long, Set<Integer>>();
  // Each user facing block has a unique block lock id.
  private AtomicInteger mBlockLockId = new AtomicInteger(0);

  private int mUserId;
  private WorkerStorage mWorkerStorage;

  public BlocksLocker(WorkerStorage workerStorage, int userId) {
    mUserId = userId;
    mWorkerStorage = workerStorage;
  }

  public synchronized boolean locked(long blockId) {
    return mLockedBlockIds.containsKey(blockId);
  }

  /**
   * Lock a block.
   * @param blockId The id of the block.
   * @return The lockId of this lock.
   */
  public synchronized int lock(long blockId) {
    int locker = mBlockLockId.incrementAndGet();
    if (!mLockedBlockIds.containsKey(blockId)) {
      try {
        mWorkerStorage.lockBlock(blockId, mUserId);
      } catch (TException e) {
        CommonUtils.runtimeException(e);
      }
      mLockedBlockIds.put(blockId, new HashSet<Integer>());
    }
    mLockedBlockIds.get(blockId).add(locker);
    return locker;
  }

  /**
   * Unlock a block with a lock id.
   * @param blockId The id of the block.
   * @param lockId The lock id of the lock.
   */
  public synchronized void unlock(long blockId, int lockId) {
    Set<Integer> lockers = mLockedBlockIds.get(blockId);
    if (lockers != null) {
      lockers.remove(lockId);
      if (lockers.isEmpty()) {
        mLockedBlockIds.remove(blockId);
        try {
          mWorkerStorage.unlockBlock(blockId, mUserId);
        } catch (TException e) {
          CommonUtils.runtimeException(e);
        }
      }
    }
  }
}
