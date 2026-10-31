export const ROUTES = {
  interview: '/interview',
  interviewCreate: (requestId: string) => `/interview/create/${requestId}`,
  interviewSession: (sessionId: string) => `/interview/session/${sessionId}`,
  resumeUpload: '/upload',
  resumeHistory: '/history',
  knowledgebaseUpload: '/knowledgebase/upload',
  login: '/login',
  register: '/register',
  engineers: '/engineers',
  review: '/review',
  reviewDetail: (id: number) => `/review/${id}`,
  invitation: (token: string) => `/invitation/${token}`,
} as const;

export const ROUTE_PATTERNS = {
  interviewCreate: 'interview/create/:requestId',
  interviewSession: 'interview/session/:activeSessionId',
} as const;
