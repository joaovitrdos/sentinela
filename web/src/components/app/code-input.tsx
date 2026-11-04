import { REGEXP_ONLY_DIGITS } from "input-otp";
import { PinInput } from "@/components/base/input/pin-input";
import { useBreakpoint } from "@/hooks/use-breakpoint";

interface CodeInputProps {
    label: string;
    value: string;
    onChange: (value: string) => void;
    onComplete?: (value: string) => void;
    error?: string;
    isDisabled?: boolean;
    autoFocus?: boolean;
}

/** Codigo de verificacao de 8 digitos enviado por e-mail pelo backend. */
export const CodeInput = ({ label, value, onChange, onComplete, error, isDisabled, autoFocus }: CodeInputProps) => {
    const wide = useBreakpoint("sm");

    return (
        <PinInput size={wide ? "xxs" : "xxxs"} invalid={!!error} disabled={isDisabled} className="items-center">
            <PinInput.Label className="sr-only">{label}</PinInput.Label>
            <PinInput.Group
                aria-label={label}
                maxLength={8}
                value={value}
                onChange={onChange}
                onComplete={onComplete}
                pattern={REGEXP_ONLY_DIGITS}
                inputMode="numeric"
                autoComplete="one-time-code"
                autoFocus={autoFocus}
                containerClassName="justify-center"
            >
                {Array.from({ length: 8 }, (_, i) => (
                    <PinInput.Slot key={i} index={i} />
                ))}
            </PinInput.Group>
            {error && <PinInput.Description isInvalid>{error}</PinInput.Description>}
        </PinInput>
    );
};
