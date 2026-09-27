/** Mirrors the backend DTOs. Fields the API omits when null are optional here. */

export type OrgRole = 'OWNER' | 'ADMIN' | 'MEMBER';
export type PlanId = 'FREE' | 'PRO' | 'SCALE';
export type SubscriptionStatus = 'TRIALING' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED' | 'INCOMPLETE';

export interface TokenResponse {
  accessToken: string;
  /** Absent when switching organizations: the existing refresh token still stands. */
  refreshToken?: string;
  tokenType: string;
  expiresInSeconds: number;
  organizationId: string;
  role: OrgRole;
}

export interface User {
  id: string;
  email: string;
  fullName: string;
  emailVerified: boolean;
  createdAt: string;
}

export interface OrganizationMembership {
  organizationId: string;
  name: string;
  slug: string;
  plan: PlanId;
  role: OrgRole;
}

export interface MeResponse {
  user: User;
  activeOrganizationId?: string;
  activeRole: OrgRole;
  organizations: OrganizationMembership[];
}

export interface Organization {
  id: string;
  name: string;
  slug: string;
  plan: PlanId;
  createdAt: string;
}

export interface Member {
  userId: string;
  email: string;
  fullName: string;
  role: OrgRole;
  joinedAt: string;
}

export interface Project {
  id: string;
  name: string;
  description?: string;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface PlanDescriptor {
  id: PlanId;
  name: string;
  monthlyPriceCents: number;
  /** -1 means unlimited. */
  maxProjects: number;
  maxSeats: number;
  monthlyApiCalls: number;
}

export interface SubscriptionSummary {
  plan: PlanId;
  status: SubscriptionStatus;
  seats: number;
  currentPeriodEnd?: string;
  cancelAtPeriodEnd: boolean;
  entitlements: PlanDescriptor;
}

export interface ApiKey {
  id: string;
  name: string;
  keyPrefix: string;
  lastUsedAt?: string;
  revokedAt?: string;
  createdAt: string;
}

export interface CreatedApiKey {
  id: string;
  name: string;
  keyPrefix: string;
  /** Present exactly once, in this response. */
  key: string;
  createdAt: string;
}

export interface MetricUsage {
  metric: string;
  used: number;
  limit: number;
  /** Absent when the metric is uncapped. */
  percentUsed?: number;
}

export interface UsageResponse {
  period: string;
  plan: PlanId;
  metrics: MetricUsage[];
}

export interface AuditEntry {
  action: string;
  target?: string;
  detail?: string;
  actorUserId?: number;
  createdAt: string;
}

/** The backend's single error shape. */
export interface ApiErrorBody {
  code: string;
  message: string;
  timestamp: string;
  fieldErrors?: Record<string, string>;
  details?: Record<string, unknown>;
}

export interface QuotaDetails {
  metric: string;
  limit: number;
  current: number;
  plan: PlanId;
}

export const UNLIMITED = -1;
