import { AlertCircle } from "@untitledui/icons";
import { Dialog, Modal, ModalOverlay } from "@/components/application/modals/modal";
import { Button } from "@/components/base/buttons/button";
import { FeaturedIcon } from "@/components/foundations/featured-icon/featured-icon";
import { FormAlert } from "@/components/app/form-alert";

interface ConfirmationModalProps {
    open: boolean;
    title: string;
    description: string;
    confirmLabel: string;
    cancelLabel: string;
    onConfirm: () => void;
    onCancel: () => void;
    busy?: boolean;
    error?: string;
    destructive?: boolean;
}

export const ConfirmationModal = ({ open, title, description, confirmLabel, cancelLabel,
    onConfirm, onCancel, busy = false, error, destructive = true }: ConfirmationModalProps) => (
    <ModalOverlay isOpen={open} onOpenChange={(value) => { if (!value && !busy) onCancel(); }}
        isDismissable={!busy} isKeyboardDismissDisabled={busy}>
        <Modal className="max-w-md">
            <Dialog aria-label={title}>
                <div className="flex w-full flex-col gap-4 p-6">
                    <FeaturedIcon icon={AlertCircle} color={destructive ? "error" : "warning"} theme="light" size="md" />
                    <div>
                        <h3 className="text-md font-semibold text-primary">{title}</h3>
                        <p className="mt-1 text-sm leading-6 break-words text-tertiary">{description}</p>
                    </div>
                    <FormAlert>{error}</FormAlert>
                    <div className="mt-1 grid grid-cols-2 gap-3">
                        <Button size="sm" color="secondary" autoFocus onClick={onCancel} isDisabled={busy}>{cancelLabel}</Button>
                        <Button size="sm" color={destructive ? "primary-destructive" : "primary"}
                            onClick={onConfirm} isDisabled={busy} isLoading={busy} showTextWhileLoading>{confirmLabel}</Button>
                    </div>
                </div>
            </Dialog>
        </Modal>
    </ModalOverlay>
);
