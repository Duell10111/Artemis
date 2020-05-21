import { DomainTagCommand } from 'app/shared/markdown-editor/domainCommands/domainTag.command';
import { addTextAtCursor } from 'app/shared/util/markdown-util';

/**
 * Insert a katex compatible formula.
 * Uses an e-function as the example.
 */
export class KatexCommand extends DomainTagCommand {
    buttonIcon = 'equals';
    buttonTranslationString = 'artemisApp.markdownEditor.commands.katex';

    /** Insert a katex formula, denoted by opening and closing '$$', into the code editor */
    execute(): void {
        const text = `${this.getOpeningIdentifier()}e^{\\frac{1}{4} y^2}${this.getClosingIdentifier()}`;
        addTextAtCursor(text, this.aceEditor);
    }

    // tslint:disable-next-line:completed-docs
    getOpeningIdentifier(): string {
        return '$$ ';
    }

    // tslint:disable-next-line:completed-docs
    getClosingIdentifier(): string {
        return ' $$';
    }
}
