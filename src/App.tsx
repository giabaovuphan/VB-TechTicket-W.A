import React, { useEffect, useState } from 'react';
import {
  DisabledAccountScreen,
  LoadingSyncScreen,
  PendingApprovalScreen,
  RejectedAccountScreen,
  SyncErrorScreen,
} from './components/AccountGateScreens';
import { AppRoute, AppShell } from './components/AppShell';
import { ErrorBoundary } from './components/ErrorBoundary';
import { AuthSessionProvider, useAuthSession } from './context/AuthSessionContext';
import { ThemeProvider } from './context/ThemeContext';
import { ToastProvider } from './context/ToastContext';
import { AdminRolesScreen } from './screens/AdminRolesScreen';
import { AuthScreen } from './screens/AuthScreen';
import { CreateTicketModal } from './screens/CreateTicketModal';
import { NotificationsScreen } from './screens/NotificationsScreen';
import { ProductCatalogScreen } from './screens/ProductCatalogScreen';
import { ReportsScreen } from './screens/ReportsScreen';
import { TicketDetailScreen } from './screens/TicketDetailScreen';
import { TicketListScreen } from './screens/TicketListScreen';
import { subscribeInventory } from './services/inventoryService';
import { subscribeProducts } from './services/productService';
import { subscribeTicketsForRole } from './services/ticketService';
import { subscribeTechnicians } from './services/userRoleService';
import { InventoryItem, ProductItem, SupportTicket, UserAccount, UserRole } from './types/models';
import { toFriendlyVietnameseError } from './utils/firestoreErrors';

const WorkspaceContent: React.FC<{
  account: UserAccount;
  role: UserRole;
  isRootAdmin: boolean;
}> = ({ account, role, isRootAdmin }) => {
  const { notifications, unreadNotificationCount, signOutAccount } = useAuthSession();
  const [route, setRoute] = useState<AppRoute>('tickets');
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  const [createModalOpen, setCreateModalOpen] = useState(false);

  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [ticketsLoading, setTicketsLoading] = useState(true);
  const [ticketsError, setTicketsError] = useState<string | null>(null);

  const [technicians, setTechnicians] = useState<UserAccount[]>([]);
  const [inventoryItems, setInventoryItems] = useState<InventoryItem[]>([]);
  const [inventoryLoading, setInventoryLoading] = useState(false);
  const [productItems, setProductItems] = useState<ProductItem[]>([]);
  const [productsLoading, setProductsLoading] = useState(false);

  const isStaff =
    role === UserRole.TECHNICIAN || role === UserRole.TEAM_LEAD || role === UserRole.ADMIN;
  const isLeadOrAdmin = role === UserRole.TEAM_LEAD || role === UserRole.ADMIN;

  useEffect(() => {
    setTicketsLoading(true);
    setTicketsError(null);
    const unsub = subscribeTicketsForRole(
      account.uid,
      role,
      (list) => {
        setTickets(list);
        setTicketsLoading(false);
      },
      (err) => {
        setTicketsError(toFriendlyVietnameseError(err, 'Không thể tải danh sách phiếu hỗ trợ.'));
        setTicketsLoading(false);
      },
    );
    return unsub;
  }, [account.uid, role]);

  useEffect(() => {
    if (!isLeadOrAdmin) {
      setTechnicians([]);
      return;
    }
    return subscribeTechnicians(
      (list) => setTechnicians(list),
      () => setTechnicians([]),
    );
  }, [isLeadOrAdmin]);

  useEffect(() => {
    if (!isStaff) {
      setInventoryItems([]);
      setProductItems([]);
      return;
    }
    setInventoryLoading(true);
    setProductsLoading(true);
    const unsubInv = subscribeInventory(
      role,
      (list) => {
        setInventoryItems(list);
        setInventoryLoading(false);
      },
      () => setInventoryLoading(false),
    );
    const unsubProd = subscribeProducts(
      role,
      (list) => {
        setProductItems(list);
        setProductsLoading(false);
      },
      () => setProductsLoading(false),
    );
    return () => {
      unsubInv();
      unsubProd();
    };
  }, [isStaff, role]);

  const handleNavigate = (nextRoute: AppRoute) => {
    setRoute(nextRoute);
    setSelectedTicketId(null);
  };

  const routeMeta: Record<AppRoute, { title: string; subtitle: string }> = {
    tickets: {
      title: selectedTicketId ? 'Chi tiết phiếu hỗ trợ' : 'Phiếu hỗ trợ kỹ thuật',
      subtitle: 'HCN Support · Tiếp nhận & xử lý sự cố theo thời gian thực',
    },
    catalog: {
      title: 'Kho vật tư & Bảng giá',
      subtitle: 'Quản lý linh kiện tồn kho và đơn giá sửa chữa chuẩn',
    },
    notifications: {
      title: 'Thông báo hệ thống',
      subtitle: 'Cập nhật phân công, tiến độ và hóa đơn sửa chữa',
    },
    reports: {
      title: 'Báo cáo & Thống kê',
      subtitle: 'Tổng hợp khối lượng xử lý và hiệu suất kỹ thuật viên',
    },
    admin: {
      title: 'Quản trị & Phân quyền',
      subtitle: 'Phê duyệt tài khoản Google và quản lý vai trò nhân sự',
    },
  };

  return (
    <>
      <AppShell
        account={account}
        role={role}
        activeRoute={route}
        onNavigate={handleNavigate}
        unreadNotifications={unreadNotificationCount}
        onCreateTicket={() => setCreateModalOpen(true)}
        onSignOut={() => void signOutAccount()}
        pageTitle={routeMeta[route].title}
        pageSubtitle={routeMeta[route].subtitle}
        showCreateButton={!selectedTicketId}
      >
        {route === 'tickets' &&
          (selectedTicketId ? (
            <TicketDetailScreen
              ticketId={selectedTicketId}
              currentUser={account}
              currentRole={role}
              technicians={technicians}
              inventoryItems={inventoryItems}
              productItems={productItems}
              onBack={() => setSelectedTicketId(null)}
            />
          ) : (
            <TicketListScreen
              tickets={tickets}
              loading={ticketsLoading}
              error={ticketsError}
              role={role}
              onSelectTicket={(id) => setSelectedTicketId(id)}
              onCreateTicket={() => setCreateModalOpen(true)}
            />
          ))}

        {route === 'catalog' && isStaff && (
          <ProductCatalogScreen
            inventoryItems={inventoryItems}
            inventoryLoading={inventoryLoading}
            productItems={productItems}
            productsLoading={productsLoading}
            currentUser={account}
            currentRole={role}
          />
        )}

        {route === 'notifications' && (
          <NotificationsScreen
            notifications={notifications}
            unreadCount={unreadNotificationCount}
            onOpenTicket={(ticketId) => {
              setRoute('tickets');
              setSelectedTicketId(ticketId);
            }}
          />
        )}

        {route === 'reports' && isLeadOrAdmin && <ReportsScreen tickets={tickets} />}

        {route === 'admin' && role === UserRole.ADMIN && (
          <AdminRolesScreen currentUser={account} isRootAdmin={isRootAdmin} />
        )}
      </AppShell>

      <CreateTicketModal
        open={createModalOpen}
        creator={account}
        onClose={() => setCreateModalOpen(false)}
        onCreated={(newTicketId) => {
          setCreateModalOpen(false);
          setRoute('tickets');
          setSelectedTicketId(newTicketId);
        }}
      />
    </>
  );
};

const AppRoot: React.FC = () => {
  const { gate, signOutAccount, refreshProfileSync } = useAuthSession();

  switch (gate.status) {
    case 'initializing':
      return <LoadingSyncScreen title="Đang khởi tạo HCN Support" subtitle="Vui lòng chờ giây lát..." />;
    case 'signed_out':
      return <AuthScreen />;
    case 'syncing':
      return <LoadingSyncScreen />;
    case 'sync_error':
      return (
        <SyncErrorScreen
          email={gate.firebaseUser.email}
          message={gate.message}
          onRetry={() => void refreshProfileSync()}
          onSignOut={() => void signOutAccount()}
        />
      );
    case 'pending':
      return (
        <PendingApprovalScreen
          account={gate.account}
          onRefresh={() => void refreshProfileSync()}
          onSignOut={() => void signOutAccount()}
        />
      );
    case 'rejected':
      return (
        <RejectedAccountScreen
          account={gate.account}
          onSignOut={() => void signOutAccount()}
        />
      );
    case 'disabled':
      return (
        <DisabledAccountScreen
          account={gate.account}
          onRefresh={() => void refreshProfileSync()}
          onSignOut={() => void signOutAccount()}
        />
      );
    case 'ready':
      return (
        <WorkspaceContent
          account={gate.account}
          role={gate.role}
          isRootAdmin={gate.isRootAdmin}
        />
      );
  }
};

export function App() {
  return (
    <ThemeProvider>
      <ErrorBoundary>
        <ToastProvider>
          <AuthSessionProvider>
            <AppRoot />
          </AuthSessionProvider>
        </ToastProvider>
      </ErrorBoundary>
    </ThemeProvider>
  );
}

export default App;
