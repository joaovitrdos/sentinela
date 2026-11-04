import { cx } from "@/utils/cx";

export const LogoMark = ({ className }: { className?: string }) => {
    return (
        <svg viewBox="0 0 32 32" fill="none" aria-hidden="true" className={cx("size-8", className)}>
            <rect width="32" height="32" rx="8" className="fill-[#0A0D12] dark:fill-white/8" />
            <path d="M16 5.5c3.6 4.3 7 8.4 7 12.6a7 7 0 1 1-14 0c0-4.2 3.4-8.3 7-12.6Z" fill="#2997FF" />
            <path d="M12.5 19.2a3.6 3.6 0 0 0 3.5 3.3" stroke="#fff" strokeOpacity=".75" strokeWidth="1.6" strokeLinecap="round" />
        </svg>
    );
};

export const Logo = ({ className }: { className?: string }) => (
    <span className={cx("inline-flex items-center gap-2.5", className)}>
        <LogoMark />
        <span className="text-lg font-semibold tracking-tight text-primary">Sentinela</span>
    </span>
);
