import { useEffect, type ReactNode } from "react";
import { notify, type NotificationType } from "@/components/app/notifications";

export const FormAlert = ({ type = "error", children }: { type?: NotificationType; children?: ReactNode; className?: string }) => {
    useEffect(() => {
        if (children) notify(children, type);
    }, [children, type]);
    return null;
};