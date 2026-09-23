import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type LeadStatus = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'APPOINTMENT' | 'ATTENDED' | 'WON' | 'LOST' | 'NO_SHOW';
export type LeakType = 'UNCONTACTED' | 'UNASSIGNED' | 'SLOW_RESPONSE' | 'QUALIFIED_NOT_PROGRESSED' | 'NO_SHOW';
export type Severity = 'HIGH' | 'MEDIUM';
export interface DetectedProblem { type: LeakType; severity: Severity }
export interface Lead { id: string; externalLeadId: string; name: string; status: LeadStatus; createdAt: string; contactedAt: string | null; assignedTo: string | null; source: string | null; campaign: string | null; appointmentAt: string | null; attendedAt: string | null; revenue: number | null; detectedProblems: DetectedProblem[] }
export type CreateLeadRequest = Omit<Lead, 'id' | 'detectedProblems'>;
export interface PagedResponse<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface RowError { row: number; field: string; message: string }
export interface UploadResult { total: number; inserted: number; updated: number; failed: number; errors: RowError[] }
export interface LeakCount { type: LeakType; severity: Severity; count: number }
export interface Summary { totalLeads: number; customers: number; recordedRevenue: number; affectedLeads: number; totalFlags: number; leakCounts: LeakCount[] }
export interface FunnelStage { name: string; count: number; conversionRate: number }
export interface Funnel { stages: FunnelStage[] }
export interface Leak { type: LeakType; severity: Severity; count: number; description: string }
export interface SourcePerformance { source: string; totalLeads: number; customers: number; recordedRevenue: number; conversionRate: number }
export interface ResponseBucket { bucket: string; count: number }
export interface LeadQuery { search?: string; status?: string; source?: string; leakType?: string; page?: number; size?: number; sort?: string }

export const LEAK_LABELS: Record<LeakType, string> = { UNCONTACTED: 'Uncontacted', UNASSIGNED: 'Unassigned', SLOW_RESPONSE: 'Slow response', QUALIFIED_NOT_PROGRESSED: 'Qualified, not progressed', NO_SHOW: 'No-show' };
export const LEAK_HELP: Record<LeakType, string> = { UNCONTACTED: 'New lead with no contact after 24 hours', UNASSIGNED: 'Open lead with no owner assigned', SLOW_RESPONSE: 'First contact took at least 60 minutes', QUALIFIED_NOT_PROGRESSED: 'Qualified lead created at least 7 days ago', NO_SHOW: 'Missed an appointment' };
export const LEAK_TYPES = Object.keys(LEAK_LABELS) as LeakType[];
export const LEAD_STATUSES: LeadStatus[] = ['NEW', 'CONTACTED', 'QUALIFIED', 'APPOINTMENT', 'ATTENDED', 'WON', 'LOST', 'NO_SHOW'];

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private readonly base = '/api';
  upload(file: File): Observable<HttpEvent<UploadResult>> { const body = new FormData(); body.append('file', file); return this.http.post<UploadResult>(`${this.base}/leads/upload`, body, { observe: 'events', reportProgress: true }); }
  leads(query: LeadQuery = {}): Observable<PagedResponse<Lead>> { let params = new HttpParams(); for (const [key, value] of Object.entries(query)) if (value !== undefined && value !== null && value !== '') params = params.set(key, String(value)); return this.http.get<PagedResponse<Lead>>(`${this.base}/leads`, { params }); }
  exportLeads(query: Omit<LeadQuery, 'page' | 'size'> = {}): Observable<Blob> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) if (value !== undefined && value !== null && value !== '') params = params.set(key, String(value));
    return this.http.get(`${this.base}/leads/export`, { params, responseType: 'blob' });
  }
  lead(id: string): Observable<Lead> { return this.http.get<Lead>(`${this.base}/leads/${encodeURIComponent(id)}`); }
  createLead(lead: CreateLeadRequest): Observable<Lead> { return this.http.post<Lead>(`${this.base}/leads`, lead); }
  summary(): Observable<Summary> { return this.http.get<Summary>(`${this.base}/dashboard/summary`); }
  funnel(): Observable<Funnel> { return this.http.get<Funnel>(`${this.base}/dashboard/funnel`); }
  leaks(): Observable<Leak[]> { return this.http.get<Leak[]>(`${this.base}/leaks`); }
  leakLeads(type: LeakType, page = 0, size = 10): Observable<PagedResponse<Lead>> { return this.http.get<PagedResponse<Lead>>(`${this.base}/leaks/${type}/leads`, { params: { page, size } }); }
  exportLeak(type: LeakType): Observable<Blob> { return this.http.get(`${this.base}/leaks/${type}/export`, { responseType: 'blob' }); }
  sources(): Observable<SourcePerformance[]> { return this.http.get<SourcePerformance[]>(`${this.base}/analytics/sources`); }
  responseTimes(): Observable<ResponseBucket[]> { return this.http.get<ResponseBucket[]>(`${this.base}/analytics/response-times`); }
}
export function errorMessage(error: unknown): string {
  const e = error as { status?: number; error?: { message?: string }; message?: string };
  if (e?.status === 0 || e?.status === 502 || e?.status === 504) {
    return 'Cannot reach the API. Start PostgreSQL and the backend (or run start-local.ps1), then retry.';
  }
  return e?.error?.message || e?.message || 'Something went wrong. Please retry.';
}
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.hidden = true;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  // Let the browser begin reading the blob before releasing its object URL.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
