import { ActiveFeatureToggles } from 'app/shared/feature-toggle/feature-toggle.service';
import { GuidedTourMapping } from 'app/guided-tour/guided-tour-setting.model';
import { ProgrammingLanguageFeature } from 'app/exercises/programming/shared/service/programming-language-feature/programming-language-feature.service';

export class ProfileInfo {
    activeProfiles: string[];
    ribbonEnv: string;
    inProduction: boolean;
    sentry?: { dsn: string };
    features: ActiveFeatureToggles;
    guidedTourMapping?: GuidedTourMapping;
    buildPlanURLTemplate: string;
    sshCloneURLTemplate: string;
    sshKeysURL: string;
    externalUserManagementURL: string;
    externalUserManagementName: string;
    imprint: string;
    contact: string;
    testServer: boolean;
    allowedMinimumOrionVersion: string;
    registrationEnabled?: boolean;
    allowedEmailPattern?: string;
    allowedEmailPatternReadable?: string;
    allowedLdapUsernamePattern?: string;
    allowedCourseRegistrationUsernamePattern?: string;
    accountName?: string;
    versionControlUrl?: string;
    programmingLanguageFeatures: ProgrammingLanguageFeature[];
}
