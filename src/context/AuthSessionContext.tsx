import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { onAuthStateChanged, signInWithPopup, signOut, type User } from 'firebase/auth';
import { auth, googleProvider } from '../firebase';
import { subscribeNotifications } from '../services/notificationService';
import { ensureUserProfileAndSyncRole, subscribeCurrentUserProfile } from '../services/userRoleService';
import {
  AccountApprovalStatus,
  isApprovedAndActive,
  isRootAdminEmail,
  parseApprovalStatus,
  parseUserRole,
  TicketNotification,
  UserAccount,
  UserRole,
} from '../types/models';
import { toFriendlyVietnameseError } from '../utils/firestoreErrors';
import { useToast } from './ToastContext';

export type AuthGateState =
  | { status: 'initializing' }
  | { status: 'signed_out' }
  | { status: 'syncing'; firebaseUser: User }
  | { status: 'sync_error'; firebaseUser: User; message: string }
  | { status: 'pending'; account: UserAccount }
  | { status: 'rejected'; account: UserAccount }
  | { status: 'disabled'; account: UserAccount }
  | { status: 'ready'; account: UserAccount; role: UserRole; isRootAdmin: boolean };

interface AuthSessionContextValue {
  gate: AuthGateState;
  authBusy: boolean;
  authError: string | null;
  notifications: TicketNotification[];
  unreadNotificationCount: number;
  signInWithGoogle: () => Promise<void>;
  signOutAccount: () => Promise<void>;
  refreshProfileSync: () => Promise<void>;
  clearAuthError: () => void;
}

const AuthSessionContext = createContext<AuthSessionContextValue | undefined>(undefined);

function deriveGateFromAccount(account: UserAccount): AuthGateState {
  const role = parseUserRole(account.role);
  const approval = parseApprovalStatus(account.approvalStatus, role);

  if (approval === AccountApprovalStatus.REJECTED) {
    return { status: 'rejected', account };
  }
  if (role === UserRole.PENDING || approval === AccountApprovalStatus.PENDING) {
    return { status: 'pending', account };
  }
  if (!isApprovedAndActive(account)) {
    return { status: 'disabled', account };
  }
  return {
    status: 'ready',
    account,
    role,
    isRootAdmin: isRootAdminEmail(account.email),
  };
}

export const AuthSessionProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { showToast } = useToast();
  const [firebaseUser, setFirebaseUser] = useState<User | null>(null);
  const [authInitialized, setAuthInitialized] = useState(false);
  const [account, setAccount] = useState<UserAccount | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [syncError, setSyncError] = useState<string | null>(null);
  const [authBusy, setAuthBusy] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [notifications, setNotifications] = useState<TicketNotification[]>([]);
  const seenNotificationIdsRef = useRef<Set<string> | null>(null);

  const syncUserAccount = useCallback(async (user: User) => {
    setSyncing(true);
    setSyncError(null);
    try {
      const synced = await ensureUserProfileAndSyncRole(user);
      setAccount(synced);
    } catch (error) {
      setSyncError(toFriendlyVietnameseError(error, 'Không thể đồng bộ hồ sơ tài khoản.'));
    } finally {
      setSyncing(false);
    }
  }, []);

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, (user) => {
      setFirebaseUser(user);
      setAuthInitialized(true);
      setAuthError(null);
      if (!user) {
        setAccount(null);
        setSyncError(null);
        setSyncing(false);
        setNotifications([]);
        seenNotificationIdsRef.current = null;
      } else {
        void syncUserAccount(user);
      }
    });
    return unsub;
  }, [syncUserAccount]);

  useEffect(() => {
    if (!firebaseUser) return;
    const unsub = subscribeCurrentUserProfile(
      firebaseUser.uid,
      (nextAccount) => {
        if (nextAccount) {
          setAccount(nextAccount);
        }
      },
      (err) => {
        console.error('Profile listener error:', err);
      },
    );
    return unsub;
  }, [firebaseUser]);

  const gate = useMemo<AuthGateState>(() => {
    if (!authInitialized) return { status: 'initializing' };
    if (!firebaseUser) return { status: 'signed_out' };
    if (syncing && !account) return { status: 'syncing', firebaseUser };
    if (syncError && !account) return { status: 'sync_error', firebaseUser, message: syncError };
    if (!account) return { status: 'syncing', firebaseUser };
    return deriveGateFromAccount(account);
  }, [account, authInitialized, firebaseUser, syncError, syncing]);

  useEffect(() => {
    if (gate.status !== 'ready') {
      setNotifications([]);
      seenNotificationIdsRef.current = null;
      return;
    }

    const uid = gate.account.uid;
    const unsub = subscribeNotifications(
      uid,
      (items) => {
        setNotifications(items);
        if (seenNotificationIdsRef.current === null) {
          seenNotificationIdsRef.current = new Set(items.map((item) => item.id));
          return;
        }
        const newlyArrived = items.filter(
          (item) => !item.readFlag && !seenNotificationIdsRef.current?.has(item.id),
        );
        for (const item of items) {
          seenNotificationIdsRef.current.add(item.id);
        }
        if (newlyArrived.length > 0) {
          const latest = newlyArrived[0];
          showToast(`${latest.title}: ${latest.message}`, false);
        }
      },
      (err) => {
        console.error('Notification listener error:', err);
      },
    );
    return unsub;
  }, [gate, showToast]);

  const signInWithGoogle = useCallback(async () => {
    setAuthBusy(true);
    setAuthError(null);
    try {
      await signInWithPopup(auth, googleProvider);
    } catch (error) {
      const code = (error as { code?: string })?.code ?? '';
      if (code === 'auth/popup-closed-by-user' || code === 'auth/cancelled-popup-request') {
        setAuthError('Bạn đã đóng cửa sổ đăng nhập Google.');
      } else if (code === 'auth/popup-blocked') {
        setAuthError('Trình duyệt đang chặn cửa sổ đăng nhập Google. Vui lòng cho phép popup và thử lại.');
      } else {
        setAuthError(toFriendlyVietnameseError(error, 'Đăng nhập Google thất bại. Vui lòng thử lại.'));
      }
    } finally {
      setAuthBusy(false);
    }
  }, []);

  const signOutAccount = useCallback(async () => {
    setAuthBusy(true);
    try {
      await signOut(auth);
    } finally {
      setAuthBusy(false);
    }
  }, []);

  const refreshProfileSync = useCallback(async () => {
    if (!firebaseUser) return;
    await syncUserAccount(firebaseUser);
  }, [firebaseUser, syncUserAccount]);

  const unreadNotificationCount = useMemo(
    () => notifications.filter((item) => !item.readFlag).length,
    [notifications],
  );

  const value = useMemo<AuthSessionContextValue>(
    () => ({
      gate,
      authBusy,
      authError,
      notifications,
      unreadNotificationCount,
      signInWithGoogle,
      signOutAccount,
      refreshProfileSync,
      clearAuthError: () => setAuthError(null),
    }),
    [
      authBusy,
      authError,
      gate,
      notifications,
      refreshProfileSync,
      signInWithGoogle,
      signOutAccount,
      unreadNotificationCount,
    ],
  );

  return <AuthSessionContext.Provider value={value}>{children}</AuthSessionContext.Provider>;
};

export function useAuthSession(): AuthSessionContextValue {
  const ctx = useContext(AuthSessionContext);
  if (!ctx) throw new Error('useAuthSession must be used within AuthSessionProvider');
  return ctx;
}
