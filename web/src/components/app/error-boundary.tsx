import { Component, type ErrorInfo, type ReactNode } from "react";

type ErrorBoundaryProps = { children: ReactNode };
type ErrorBoundaryState = { failed: boolean };

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
    state: ErrorBoundaryState = { failed: false };

    static getDerivedStateFromError(): ErrorBoundaryState {
        return { failed: true };
    }

    componentDidCatch(error: Error, info: ErrorInfo) {
        console.error("Falha ao renderizar a aplicação", error, info.componentStack);
    }

    render() {
        if (this.state.failed) {
            return (
                <main className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-primary px-6 text-center">
                    <h1 className="text-xl font-semibold text-primary">Não foi possível abrir esta tela.</h1>
                    <p className="max-w-md text-sm text-tertiary">Recarregue a página para tentar novamente.</p>
                    <button type="button" className="rounded-lg bg-brand-solid px-4 py-2 text-sm font-semibold text-white" onClick={() => window.location.reload()}>
                        Recarregar
                    </button>
                </main>
            );
        }
        return this.props.children;
    }
}
