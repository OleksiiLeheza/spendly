export * from './households.service';
import { HouseholdsApi } from './households.service';
export * from './households.serviceInterface';
export * from './users.service';
import { UsersApi } from './users.service';
export * from './users.serviceInterface';
export const APIS = [HouseholdsApi, UsersApi];
