import { StrictMode, Suspense, lazy } from "react";
import { AnimatePresence, MotionConfig } from "motion/react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Route, Routes, useLocation } from "react-router";
import { AppShell } from "@/components/app/app-shell";
import { ErrorBoundary } from "@/components/app/error-boundary";
import { Notifications } from "@/components/app/notifications";
import { ConnectionMonitor } from "@/components/app/connection-monitor";
import { AnimatedOutlet, PageFade, routeGroup } from "@/components/app/page-transition";
import { PlanGate } from "@/components/app/plan-gate";
import { GuestOnly, PageLoader, Redirect, RequireAuth } from "@/components/app/route-guards";
import "@/i18n";
import { AuthPage } from "@/pages/auth/auth-page";
import { ForgotPasswordPage } from "@/pages/auth/forgot-password-page";
import { ResetPasswordPage } from "@/pages/auth/reset-password-page";
import { VerifyEmailPage } from "@/pages/auth/verify-email-page";
import { HomePage } from "@/pages/home-page";
import { LegalPage } from "@/pages/legal-page";
import { MaintenancePage } from "@/pages/maintenance-page";
import { NotFound } from "@/pages/not-found";
import { PlansPage } from "@/pages/plans-page";
import { SettingsPage } from "@/pages/settings-page";
import { AuthProvider } from "@/providers/auth-provider";
import { BillingProvider } from "@/providers/billing-provider";
import { RouteProvider } from "@/providers/router-provider";
import { SystemProvider, useSystem } from "@/providers/system-provider";
import { ThemeProvider } from "@/providers/theme-provider";
import "@/styles/globals.css";

const DashboardPage = lazy(() => import("@/pages/dashboard-page").then((module) => ({ default: module.DashboardPage })));
const PropertiesPage = lazy(() => import("@/pages/properties-page").then((module) => ({ default: module.PropertiesPage })));
const ReportsPage = lazy(() => import("@/pages/reports-page").then((module) => ({ default: module.ReportsPage })));

const AppRoutes = () => {
    const location = useLocation();
    const { status, ready } = useSystem();

    if (!ready) return <PageLoader />;
    if (status?.maintenance)
        return (
            <PageFade>
                <MaintenancePage />
            </PageFade>
        );

    return (
        // Troca de "moldura" (login -> inicio, inicio -> 404...) anima a pagina inteira.
        <AnimatePresence mode="wait" initial={false} onExitComplete={() => window.scrollTo({ top: 0, behavior: "instant" })}>
            <PageFade key={routeGroup(location.pathname)}>
                <Routes location={location}>
                    <Route path="/" element={<Redirect to="/home" />} />
                    <Route path="/404" element={<NotFound />} />
                    <Route path="/maintenance" element={<Redirect to="/home" />} />

                    {/* AuthController. Rota-pai unica: trocar entre /login e /register nao desmonta a tela (nem o mapa) */}
                    <Route
                        element={
                            <GuestOnly>
                                <AuthPage />
                            </GuestOnly>
                        }
                    >
                        <Route path="/login" element={null} />
                        <Route path="/register" element={null} />
                    </Route>
                    <Route
                        path="/verify-email"
                        element={
                            <GuestOnly>
                                <VerifyEmailPage />
                            </GuestOnly>
                        }
                    />
                    <Route
                        path="/forgot-password"
                        element={
                            <GuestOnly>
                                <ForgotPasswordPage />
                            </GuestOnly>
                        }
                    />
                    <Route
                        path="/reset-password"
                        element={
                            <GuestOnly>
                                <ResetPasswordPage />
                            </GuestOnly>
                        }
                    />

                    {/* Telas com cabecalho: o cabecalho fica montado e so o conteudo anima */}
                    <Route
                        element={
                            <AppShell>
                                <AnimatedOutlet />
                            </AppShell>
                        }
                    >
                        <Route
                            path="/home"
                            element={
                                <RequireAuth>
                                    <HomePage />
                                </RequireAuth>
                            }
                        />
                        <Route
                            path="/properties"
                            element={
                                <RequireAuth>
                                    <Suspense fallback={<PageLoader />}>
                                        <PropertiesPage />
                                    </Suspense>
                                </RequireAuth>
                            }
                        />
                        <Route
                            path="/dashboard"
                            element={
                                <RequireAuth>
                                    <Suspense fallback={<PageLoader />}>
                                        <DashboardPage />
                                    </Suspense>
                                </RequireAuth>
                            }
                        />
                        <Route
                            path="/reports"
                            element={
                                <RequireAuth>
                                    <Suspense fallback={<PageLoader />}>
                                        <PlanGate feature="REPORTS">
                                            <ReportsPage />
                                        </PlanGate>
                                    </Suspense>
                                </RequireAuth>
                            }
                        />
                        <Route
                            path="/settings"
                            element={
                                <RequireAuth>
                                    <SettingsPage />
                                </RequireAuth>
                            }
                        />
                        <Route path="/plans" element={<PlansPage />} />
                        <Route path="/privacy" element={<LegalPage doc="privacy" />} />
                        <Route path="/terms" element={<LegalPage doc="terms" />} />
                    </Route>

                    <Route path="*" element={<NotFound />} />
                </Routes>
            </PageFade>
        </AnimatePresence>
    );
};

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        <ErrorBoundary>
            {/* reducedMotion="user": quem pede "reduzir movimento" no sistema ve tudo sem animacao */}
            <MotionConfig reducedMotion="user">
                <ThemeProvider>
                    <Notifications />
                    <ConnectionMonitor />
                    <SystemProvider>
                        <AuthProvider>
                            <BillingProvider>
                                <BrowserRouter>
                                    <RouteProvider>
                                        <AppRoutes />
                                    </RouteProvider>
                                </BrowserRouter>
                            </BillingProvider>
                        </AuthProvider>
                    </SystemProvider>
                </ThemeProvider>
            </MotionConfig>
        </ErrorBoundary>
    </StrictMode>,
);
